package com.org.playboard.service.stats;

import com.org.playboard.dto.stats.LeaderboardEntryDto;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Ranks a group's players by a confidence-adjusted win rate.
 *
 * <p>Ranking on raw win% is hostile to volume: a player at 6-1 (86%, 7 games) outranks one
 * at 27-23 (54%, 50 games), so a newcomer's hot streak beats months of play. Weighting games
 * played instead is worse — any formula where volume <em>adds</em> score lets someone climb
 * by losing often. This uses the <b>Wilson score lower bound</b>, which answers "what win
 * rate are we confident this player is at least worth": small samples are discounted
 * automatically, and a rating rises as games accumulate even at a constant win rate.
 *
 * <p>Players below {@link #minGamesToRank} are <em>provisional</em> — listed, but after the
 * ranked players. That threshold <b>slides with group activity</b> rather than being a fixed
 * number. This matters at the start of a month when everyone has two or three games: a fixed
 * gate would empty the board, whereas a relative one ranks everybody, because nobody holds an
 * evidence advantage over anyone else. The gate exists to stop unfair <i>small-N vs large-N</i>
 * comparison; when every N is small there is nothing to prevent.
 *
 * <p>Deliberately free of Spring and JPA so both the all-time and the windowed leaderboard
 * paths can share one implementation. They previously ordered separately — one in JPQL, one
 * in a Java comparator — which the old code flagged as able to "report a rank change that
 * never happened".
 */
public final class LeaderboardRanker {

    /**
     * 1.96 = 95% confidence. Raising it punishes small samples harder, but overshoots: at
     * 99% a 6-4 player falls below a 42-78 one, which is worse than the problem being fixed.
     * Tune {@link #minGamesToRank} instead.
     */
    private static final double Z = 1.96d;

    /** Ceiling on the sliding threshold: past this, more evidence stops being required. */
    private static final int MAX_MIN_GAMES = 10;

    private LeaderboardRanker() {}

    /** A player's raw totals for the window being ranked, before any ordering. */
    public record RawStatRow(
            UUID userId,
            int gamesPlayed,
            int wins,
            int pointsFor,
            int pointsAgainst,
            int currentStreak,
            int bestStreak) {}

    /** Ranked entries plus the threshold they were partitioned on. */
    public record Standings(List<LeaderboardEntryDto> entries, int minGamesToRank) {}

    /**
     * Wilson score lower bound for {@code wins} of {@code games}, scaled to 0-100.
     *
     * <p>{@code ((p + z²/2n) − z·√(p(1−p)/n + z²/4n²)) / (1 + z²/n)}, where {@code p = wins/n}.
     * Returns 0 for zero games rather than dividing by zero — a NaN escaping into the
     * comparator would corrupt the whole ordering rather than fail loudly.
     */
    public static double wilsonLowerBound(int wins, int games) {
        if (games <= 0) {
            return 0d;
        }
        double n = games;
        double p = wins / n;
        double z2 = Z * Z;
        double centre = p + z2 / (2 * n);
        double margin = Z * Math.sqrt((p * (1 - p) + z2 / (4 * n)) / n);
        return Math.max(0d, (centre - margin) / (1 + z2 / n)) * 100d;
    }

    /**
     * Games a player needs before they rank: half the group's median games played, rounded
     * up, clamped to {@code [1, 10]}.
     *
     * <p>Median (not mean) so one player who turns up to everything doesn't raise the bar for
     * the group. Players with zero games are excluded — they'd drag the median down and make
     * the threshold meaninglessly low. An even-length set takes the mean of the two middle
     * values, and the order of operations is median → halve → round up → clamp.
     */
    public static int minGamesToRank(Collection<Integer> gamesPlayed) {
        List<Integer> active = gamesPlayed.stream().filter(g -> g > 0).sorted().toList();
        if (active.isEmpty()) {
            return 1;
        }
        int size = active.size();
        double median = size % 2 == 1
                ? active.get(size / 2)
                : (active.get(size / 2 - 1) + active.get(size / 2)) / 2d;
        int threshold = (int) Math.ceil(median / 2d);
        return Math.max(1, Math.min(MAX_MIN_GAMES, threshold));
    }

    /**
     * Orders {@code rows} into a leaderboard.
     *
     * <p>Ranked players come first, sorted by rating; provisional players follow, sorted the
     * same way. Ranks are dense over the ranked block and then continue through the
     * provisional one, so no entry carries a sentinel rank.
     *
     * @param thresholdOverride pins the threshold instead of deriving it, so two standings
     *     can be compared without the group median shifting between them. Passing {@code null}
     *     derives it. See {@code SessionRankChangeJob} — without pinning, one player's games
     *     can move the median, reshuffle the ranked set, and fire rank-change notifications
     *     at people who never played.
     */
    public static Standings rank(
            List<RawStatRow> rows,
            Integer thresholdOverride,
            EntryFactory entryFactory) {
        int threshold = thresholdOverride != null
                ? thresholdOverride
                : minGamesToRank(rows.stream().map(RawStatRow::gamesPlayed).toList());

        List<LeaderboardEntryDto> ranked = new ArrayList<>();
        List<LeaderboardEntryDto> provisional = new ArrayList<>();
        for (RawStatRow row : rows) {
            if (row.gamesPlayed() == 0) {
                continue;
            }
            boolean isProvisional = row.gamesPlayed() < threshold;
            LeaderboardEntryDto entry = entryFactory.create(row, rating(row), isProvisional);
            (isProvisional ? provisional : ranked).add(entry);
        }

        ranked.sort(CANONICAL_ORDER);
        provisional.sort(CANONICAL_ORDER);

        List<LeaderboardEntryDto> out = new ArrayList<>(ranked.size() + provisional.size());
        int rank = 1;
        for (LeaderboardEntryDto e : ranked) {
            out.add(e.withRank(rank++));
        }
        for (LeaderboardEntryDto e : provisional) {
            out.add(e.withRank(rank++));
        }
        return new Standings(out, threshold);
    }

    /** Builds the DTO for a row; supplied by the caller since only it can resolve the user. */
    @FunctionalInterface
    public interface EntryFactory {
        LeaderboardEntryDto create(RawStatRow row, BigDecimal rating, boolean provisional);
    }

    /** Wilson lower bound as it goes on the wire: 0-100, one decimal. */
    public static BigDecimal rating(RawStatRow row) {
        return BigDecimal.valueOf(wilsonLowerBound(row.wins(), row.gamesPlayed()))
                .setScale(1, RoundingMode.HALF_UP);
    }

    /** Win rate as a 4-dp fraction, matching the {@code member_stats.win_rate} column's scale. */
    public static BigDecimal winRate(int wins, int gamesPlayed) {
        if (gamesPlayed == 0) {
            return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(wins).divide(BigDecimal.valueOf(gamesPlayed), 4, RoundingMode.HALF_UP);
    }

    /**
     * Rating desc, then points difference desc, then wins desc, then user id.
     *
     * <p>Sorts on the <b>unrounded</b> bound via {@link #wilsonLowerBound}: at one decimal
     * place displayed ties are common, so ordering the rounded value would shuffle between
     * requests. Points difference stays the first tiebreak because it is rendered on the row,
     * making the order between two equal ratings visible rather than hidden. The trailing
     * user id keeps fully tied rows stable across requests.
     */
    private static final Comparator<LeaderboardEntryDto> CANONICAL_ORDER = Comparator
            .comparingDouble((LeaderboardEntryDto e) -> wilsonLowerBound(e.wins(), e.gamesPlayed()))
            .reversed()
            .thenComparing(Comparator.comparingInt(LeaderboardEntryDto::pointsDiff).reversed())
            .thenComparing(Comparator.comparingInt(LeaderboardEntryDto::wins).reversed())
            .thenComparing(LeaderboardEntryDto::userId);
}
