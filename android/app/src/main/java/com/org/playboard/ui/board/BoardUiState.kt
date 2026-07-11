package com.org.playboard.ui.board

import com.org.playboard.data.model.Group
import com.org.playboard.data.model.PlayerRanking

/** Columns the rankings table can be sorted by (docs/requirements/02-board-leaderboard.md #3). */
enum class RankingSortColumn {
    GAMES_PLAYED, WINS, LOSSES, POINTS_FOR, WIN_RATE
}

/**
 * Immutable state for the Board tab. The active group is chosen from the shared
 * group switcher (see [com.org.playboard.ui.switcher.GroupSwitcher]); this
 * ViewModel just observes it and loads the leaderboard. [rankings] keeps the
 * server order (win rate desc — the canonical ranking); [tableRows] applies the
 * user's chosen sort on top of it for display.
 */
data class BoardUiState(
    val isLoading: Boolean = true,
    val hasLoadFailed: Boolean = false,
    /** A user-initiated pull-to-refresh is in flight (drives the pull indicator). */
    val isRefreshing: Boolean = false,
    val selectedGroup: Group? = null,
    val rankings: List<PlayerRanking> = emptyList(),
    val sortColumn: RankingSortColumn = RankingSortColumn.WIN_RATE,
) {
    /** Top 3 by canonical ranking — always the first entries of the server-sorted list. */
    val podium: List<PlayerRanking> get() = rankings.take(3)

    /**
     * Rows in display order. `sortedByDescending` is stable, so players tied
     * on the chosen column keep their canonical (win-rate) relative order.
     */
    val tableRows: List<PlayerRanking> get() = when (sortColumn) {
        RankingSortColumn.WIN_RATE -> rankings
        RankingSortColumn.GAMES_PLAYED -> rankings.sortedByDescending { it.gamesPlayed }
        RankingSortColumn.WINS -> rankings.sortedByDescending { it.wins }
        RankingSortColumn.LOSSES -> rankings.sortedByDescending { it.losses }
        RankingSortColumn.POINTS_FOR -> rankings.sortedByDescending { it.pointsFor }
    }
}
