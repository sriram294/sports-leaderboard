package com.org.playboard.ui.board

import com.org.playboard.data.model.Group
import com.org.playboard.data.model.PlayerRanking
import com.org.playboard.data.model.UserSession

/** Columns the rankings table can be sorted by (docs/requirements/02-board-leaderboard.md #3). */
enum class RankingSortColumn {
    GAMES_PLAYED, WINS, LOSSES, POINTS_FOR, WIN_RATE
}

/** Whether the create/join sheet is creating a new group or joining one by code. */
enum class GroupActionMode { CREATE, JOIN }

/** Reason a create/join attempt failed, mapped to a user-facing message in the sheet. */
enum class GroupActionError { INVALID_CODE, NETWORK }

/**
 * State of the "create or join a group" bottom sheet. `null` on [BoardUiState]
 * means the sheet is closed.
 */
data class GroupActionSheetState(
    val mode: GroupActionMode = GroupActionMode.CREATE,
    val input: String = "",
    val isSubmitting: Boolean = false,
    val error: GroupActionError? = null,
) {
    /** Submit is allowed only with non-blank input and no in-flight request. */
    val canSubmit: Boolean get() = input.isNotBlank() && !isSubmitting
}

/**
 * Immutable state for the Board tab. [rankings] keeps the server order
 * (win rate desc — the canonical ranking); [tableRows] applies the user's
 * chosen sort on top of it for display.
 */
data class BoardUiState(
    val isLoading: Boolean = true,
    val hasLoadFailed: Boolean = false,
    val currentUser: UserSession? = null,
    val groups: List<Group> = emptyList(),
    val selectedGroup: Group? = null,
    val isGroupSwitcherExpanded: Boolean = false,
    val rankings: List<PlayerRanking> = emptyList(),
    val sortColumn: RankingSortColumn = RankingSortColumn.WIN_RATE,
    val groupActionSheet: GroupActionSheetState? = null,
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
