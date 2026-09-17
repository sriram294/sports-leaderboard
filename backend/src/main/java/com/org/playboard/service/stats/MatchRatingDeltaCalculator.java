package com.org.playboard.service.stats;

import com.org.playboard.service.stats.LeaderboardRanker.RawStatRow;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Rating points a player gained or lost from one specific match: the difference between the
 * unrounded Wilson values computed immediately before and immediately after that match in the
 * player's chronological history, rounded to two decimals for the API.
 *
 * <p>Deliberately a sibling of {@link LeaderboardRanker} rather than a method on it —
 * {@link LeaderboardRanker}'s purpose is ranking/ordering a whole leaderboard, and stretching
 * its Javadoc to also cover a single match's before/after delta would blur that. Pure and
 * Spring-free for the same reason as {@link LeaderboardRanker}: it's the one place the maths
 * is invisible in the UI, so tests need to reach it without a database.
 */
public final class MatchRatingDeltaCalculator {

    private MatchRatingDeltaCalculator() {}

    /**
     * @param resultsChronological one player's match results in one group, oldest first —
     *     exactly what {@code MatchParticipantRepository.findPlayerMatchHistory} returns.
     * @param targetIndex the 0-based position of the match being scored within that list.
     */
    public static BigDecimal delta(List<Boolean> resultsChronological, int targetIndex) {
        int gamesBefore = targetIndex;
        int winsBefore = (int) resultsChronological.subList(0, targetIndex).stream().filter(b -> b).count();
        return delta(gamesBefore, winsBefore, resultsChronological.get(targetIndex));
    }

    /** Bulk-replay variant for callers that already hold the player's pre-match totals. */
    public static BigDecimal delta(int gamesBefore, int winsBefore, boolean won) {
        int gamesAfter = gamesBefore + 1;
        int winsAfter = winsBefore + (won ? 1 : 0);

        BigDecimal before = BigDecimal.valueOf(LeaderboardRanker.wilsonLowerBound(winsBefore, gamesBefore));
        BigDecimal after = BigDecimal.valueOf(LeaderboardRanker.wilsonLowerBound(winsAfter, gamesAfter));
        return after.subtract(before).setScale(2, RoundingMode.HALF_UP);
    }
}
