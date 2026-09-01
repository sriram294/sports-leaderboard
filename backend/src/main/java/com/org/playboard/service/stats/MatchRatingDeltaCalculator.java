package com.org.playboard.service.stats;

import com.org.playboard.service.stats.LeaderboardRanker.RawStatRow;
import java.math.BigDecimal;
import java.util.List;

/**
 * Rating points a player gained or lost from one specific match: the difference between
 * {@link LeaderboardRanker#rating} computed immediately before and immediately after that
 * match in the player's chronological history.
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
        int gamesAfter = gamesBefore + 1;
        int winsAfter = winsBefore + (resultsChronological.get(targetIndex) ? 1 : 0);

        BigDecimal before = LeaderboardRanker.rating(new RawStatRow(null, gamesBefore, winsBefore, 0, 0, 0, 0));
        BigDecimal after = LeaderboardRanker.rating(new RawStatRow(null, gamesAfter, winsAfter, 0, 0, 0, 0));
        return after.subtract(before);
    }
}
