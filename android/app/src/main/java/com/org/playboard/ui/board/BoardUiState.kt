package com.org.playboard.ui.board

import com.org.playboard.data.model.Group
import com.org.playboard.data.model.PlayerRanking

/**
 * Immutable state for the Board tab. The active group is chosen from the shared
 * group switcher (see [com.org.playboard.ui.switcher.GroupSwitcher]); this
 * ViewModel just observes it and loads the leaderboard. [rankings] keeps the
 * server order (rating desc — the canonical ranking, with provisional players already
 * placed last); [tableRows] applies the user's chosen metric on top of it for display.
 */
data class BoardUiState(
    val isLoading: Boolean = true,
    val hasLoadFailed: Boolean = false,
    /** A user-initiated pull-to-refresh is in flight (drives the pull indicator). */
    val isRefreshing: Boolean = false,
    val selectedGroup: Group? = null,
    val rankings: List<PlayerRanking> = emptyList(),
    val sortMetric: RankingSortMetric = RankingSortMetric.RATING,
    /** Games needed before a player ranks rather than showing as provisional. */
    val minGamesToRank: Int = 1,
    /** Calendar window the leaderboard is scoped to; drives the "TOP PLAYERS" toggle. */
    val selectedTimeRange: LeaderboardTimeRange = LeaderboardTimeRange.MONTH,
    /** The signed-in user's most recent results in this group, newest first (≤5). `true` = win. */
    val recentForm: List<Boolean> = emptyList(),
) {
    /** Players over the games threshold, in canonical order. */
    val rankedPlayers: List<PlayerRanking> get() = rankings.filter { !it.provisional }

    /**
     * Top 3 by canonical ranking. Provisional players are excluded: crowning someone the
     * table below refuses to rank would contradict it, and "best player" shouldn't be
     * decided by a hot streak over a handful of games.
     */
    val podium: List<PlayerRanking> get() = rankedPlayers.take(3)

    /**
     * Whether the pinned "YOUR FORM" bar shows. It's hidden for a player with no
     * matches in the group, and never floats over a spinner / empty / error state.
     */
    val showFormBar: Boolean get() =
        recentForm.isNotEmpty() && !isLoading && !hasLoadFailed && rankings.isNotEmpty()

    /**
     * Rows in display order.
     *
     * Partitioned first, then sorted within each partition, so provisional players stay
     * below every ranked one whatever the metric — re-sorting by games played would
     * otherwise float a 3-game newcomer back to the top. `sortedByDescending` is stable,
     * so players tied on the chosen metric keep their canonical relative order.
     */
    val tableRows: List<PlayerRanking> get() {
        val (provisional, ranked) = rankings.partition { it.provisional }
        return ranked.sortedByMetric(sortMetric) + provisional.sortedByMetric(sortMetric)
    }

    private fun List<PlayerRanking>.sortedByMetric(metric: RankingSortMetric) = when (metric) {
        // Already in rating order from the server; re-sorting would drop its tiebreaks.
        RankingSortMetric.RATING -> this
        RankingSortMetric.WIN_RATE -> sortedByDescending { it.winRate }
        RankingSortMetric.GAMES -> sortedByDescending { it.gamesPlayed }
        RankingSortMetric.POINTS_DIFF -> sortedByDescending { it.pointsDiff }
    }
}
