package com.org.playboard.service.stats;

import static com.org.playboard.service.stats.MonthlyTrophyJob.completedMonths;
import static com.org.playboard.service.stats.MonthlyTrophyJob.pickWinner;
import static org.assertj.core.api.Assertions.assertThat;

import com.org.playboard.dto.stats.LeaderboardEntryDto;
import com.org.playboard.service.stats.LeaderboardRanker.RawStatRow;
import com.org.playboard.service.stats.LeaderboardRanker.Standings;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Pure trophy-award logic — no Spring, no DB.
 *
 * <p>Both halves are worth pinning: the month arithmetic decides <em>when</em> a crown is
 * awarded and is invisible until it is wrong by a whole month, and winner selection decides
 * <em>whether</em> one is awarded at all.
 */
class MonthlyTrophyJobTest {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private static UUID id(int n) {
        return UUID.fromString("00000000-0000-0000-0000-%012d".formatted(n));
    }

    /** Standings built the same way the real ranker builds them, so provisional is honest. */
    private static Standings standings(List<RawStatRow> rows) {
        return LeaderboardRanker.rank(rows, null, (r, rating, provisional) -> new LeaderboardEntryDto(
                0, r.userId(), r.userId().toString(), null, null, "#000000",
                r.gamesPlayed(), r.wins(), r.gamesPlayed() - r.wins(),
                r.pointsFor(), r.pointsAgainst(),
                LeaderboardRanker.winRate(r.wins(), r.gamesPlayed()),
                0, 0, rating, provisional));
    }

    private static RawStatRow row(int idN, int games, int wins) {
        return new RawStatRow(id(idN), games, wins, 0, 0, 0, 0);
    }

    @Nested
    @DisplayName("which months are ready to award")
    class CompletedMonths {

        @Test
        void the_current_month_is_never_awarded() {
            // Mid-July: June is done, July is still being played.
            List<YearMonth> months = completedMonths(
                    Instant.parse("2026-06-03T10:00:00Z"), Instant.parse("2026-07-21T10:00:00Z"), IST);

            assertThat(months).containsExactly(YearMonth.of(2026, 6));
        }

        @Test
        void a_group_that_only_started_this_month_has_nothing_to_award() {
            // The state this feature launches into: play began in July, July isn't over.
            List<YearMonth> months = completedMonths(
                    Instant.parse("2026-07-05T10:00:00Z"), Instant.parse("2026-07-21T10:00:00Z"), IST);

            assertThat(months).isEmpty();
        }

        @Test
        void every_month_since_the_first_match_is_enumerated() {
            // Gaps don't matter — a month with no play still gets a verdict, so the job
            // stops reconsidering it.
            List<YearMonth> months = completedMonths(
                    Instant.parse("2026-03-14T10:00:00Z"), Instant.parse("2026-07-01T10:00:00Z"), IST);

            assertThat(months)
                    .containsExactly(
                            YearMonth.of(2026, 3),
                            YearMonth.of(2026, 4),
                            YearMonth.of(2026, 5),
                            YearMonth.of(2026, 6));
        }

        @Test
        void the_year_rolls_over() {
            List<YearMonth> months = completedMonths(
                    Instant.parse("2025-11-02T10:00:00Z"), Instant.parse("2026-01-15T10:00:00Z"), IST);

            assertThat(months).containsExactly(YearMonth.of(2025, 11), YearMonth.of(2025, 12));
        }

        @Test
        void the_zone_decides_which_month_an_instant_belongs_to() {
            // 2026-07-31T20:00Z is already 1 August in IST (+05:30). Read in UTC the group
            // would still be mid-July and August would not yet be awardable; read in IST,
            // July is complete. This off-by-one-month is the whole reason the zone is
            // explicit rather than inherited from the server's default.
            Instant justAfterMidnightIst = Instant.parse("2026-07-31T20:00:00Z");
            Instant firstMatch = Instant.parse("2026-06-05T10:00:00Z");

            assertThat(completedMonths(firstMatch, justAfterMidnightIst, IST))
                    .contains(YearMonth.of(2026, 7));
            assertThat(completedMonths(firstMatch, justAfterMidnightIst, ZoneId.of("UTC")))
                    .doesNotContain(YearMonth.of(2026, 7));
        }
    }

    @Nested
    @DisplayName("who wins the month")
    class PickWinner {

        @Test
        void the_top_ranked_player_wins() {
            // Six players at 10 games each: threshold is 5, so everyone is ranked.
            Standings s = standings(List.of(
                    row(1, 10, 9), row(2, 10, 7), row(3, 10, 5),
                    row(4, 10, 4), row(5, 10, 2), row(6, 10, 1)));

            assertThat(pickWinner(s)).map(LeaderboardEntryDto::userId).contains(id(1));
        }

        @Test
        void a_provisional_player_cannot_win_even_with_a_perfect_record() {
            // The motivating case: someone turns up once, wins, and would otherwise take the
            // month off players who showed up all month. The ranker puts provisional players
            // last, so a 2-0 newcomer sits below the regulars regardless of win rate.
            Standings s = standings(List.of(
                    row(1, 2, 2), row(2, 20, 12), row(3, 20, 11), row(4, 20, 6)));

            assertThat(pickWinner(s)).map(LeaderboardEntryDto::userId).contains(id(2));
        }

        @Test
        void a_month_where_everyone_is_provisional_has_no_winner() {
            // Not reachable via the sliding threshold, which adapts to a quiet group — so
            // this pins the guard directly, with a threshold pinned high enough to strand
            // everybody. Without the guard the crown would go to whoever played twice.
            Standings s = LeaderboardRanker.rank(
                    List.of(row(1, 2, 2), row(2, 3, 1)),
                    10,
                    (r, rating, provisional) -> new LeaderboardEntryDto(
                            0, r.userId(), r.userId().toString(), null, null, "#000000",
                            r.gamesPlayed(), r.wins(), r.gamesPlayed() - r.wins(),
                            0, 0, LeaderboardRanker.winRate(r.wins(), r.gamesPlayed()),
                            0, 0, rating, provisional));

            assertThat(pickWinner(s)).isEmpty();
        }

        @Test
        void a_month_with_no_play_at_all_has_no_winner() {
            assertThat(pickWinner(standings(List.of()))).isEmpty();
        }
    }
}
