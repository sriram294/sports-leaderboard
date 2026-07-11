package com.org.playboard.ui.switcher

import com.org.playboard.data.group.GroupsLoadState
import com.org.playboard.data.model.Group

/** Whether the create/join sheet is creating a new group or joining one by code. */
enum class GroupActionMode { CREATE, JOIN }

/** Reason a create/join attempt failed, mapped to a user-facing message in the sheet. */
enum class GroupActionError { INVALID_CODE, NETWORK }

/**
 * State of the "create or join a group" bottom sheet. `null` on
 * [GroupSwitcherUiState] means the sheet is closed.
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
 * State of the "invite players" bottom sheet. `null` on [GroupSwitcherUiState]
 * means it's closed. While [isLoading] the code is being generated; [code] holds
 * the generated invite once ready; [hasFailed] marks a (retryable) failure.
 */
data class InviteSheetState(
    val groupName: String,
    val isLoading: Boolean = true,
    val code: String? = null,
    val hasFailed: Boolean = false,
)

/**
 * State of the shared group switcher shown at the top of every tab
 * (docs/requirements/00-overview.md § Group). The active group and the group
 * list live in [com.org.playboard.data.group.GroupRepository]; this mirrors them
 * for the header plus the switcher's own local UI (expanded panel, sheets).
 */
data class GroupSwitcherUiState(
    val groups: List<Group> = emptyList(),
    val selectedGroup: Group? = null,
    val loadState: GroupsLoadState = GroupsLoadState.LOADING,
    val isExpanded: Boolean = false,
    val groupActionSheet: GroupActionSheetState? = null,
    val inviteSheet: InviteSheetState? = null,
)
