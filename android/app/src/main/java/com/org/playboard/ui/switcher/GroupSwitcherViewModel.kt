package com.org.playboard.ui.switcher

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.org.playboard.data.group.GroupRepository
import com.org.playboard.data.group.GroupsLoadState
import com.org.playboard.data.remote.InvalidInviteCodeException
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The shared group switcher shown at the top of every tab
 * (docs/requirements/00-overview.md § Group). Owns loading the user's groups
 * and switching the active one, plus the create/join and invite sheets. The
 * active group lives in [GroupRepository], so each tab's ViewModel observes it
 * and reloads its own data when the switcher changes it here.
 */
@HiltViewModel
class GroupSwitcherViewModel @Inject constructor(
    private val groupRepository: GroupRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(GroupSwitcherUiState())
    val uiState: StateFlow<GroupSwitcherUiState> = _uiState.asStateFlow()

    init {
        // Mirror the app-wide group list / active group / load status into the header.
        viewModelScope.launch {
            combine(
                groupRepository.groups,
                groupRepository.selectedGroup,
                groupRepository.groupsLoadState,
            ) { groups, selected, loadState ->
                Triple(groups, selected, loadState)
            }.collect { (groups, selected, loadState) ->
                _uiState.update { it.copy(groups = groups, selectedGroup = selected, loadState = loadState) }
            }
        }
        // A group created/joined/left elsewhere, or a match recorded, can change
        // member/match counts — re-fetch the list when the data revision advances.
        viewModelScope.launch {
            groupRepository.dataRevision.drop(1).collect { refresh() }
        }
        refresh()
    }

    /** Re-fetches the user's groups. Also the retry path when the list fails to load. */
    fun refresh() {
        viewModelScope.launch { groupRepository.refreshGroups() }
    }

    fun onToggled() {
        _uiState.update { it.copy(isExpanded = !it.isExpanded) }
    }

    fun onGroupSelected(groupId: String) {
        groupRepository.selectGroup(groupId)
        _uiState.update { it.copy(isExpanded = false) }
    }

    /** Opens the create/join sheet (and collapses the switcher behind it). */
    fun onCreateOrJoinGroupClicked() {
        _uiState.update { it.copy(isExpanded = false, groupActionSheet = GroupActionSheetState()) }
    }

    /**
     * Opens the invite sheet for the active group and starts generating a code.
     * No-op if there's no active group; the entry point is gated on
     * [com.org.playboard.data.model.Group.canInvite] so only owners/admins reach it.
     */
    fun onInvitePlayersClicked() {
        val group = _uiState.value.selectedGroup ?: return
        _uiState.update {
            it.copy(isExpanded = false, inviteSheet = InviteSheetState(groupName = group.name))
        }
        generateInvite(group.id)
    }

    fun onInviteRetry() {
        val group = _uiState.value.selectedGroup ?: return
        updateInviteSheet { it.copy(isLoading = true, hasFailed = false) }
        generateInvite(group.id)
    }

    fun onInviteSheetDismissed() {
        _uiState.update { it.copy(inviteSheet = null) }
    }

    /**
     * Opens the rename sheet for the active group, seeded with its current name.
     * No-op if there's no active group; the entry point is gated on
     * [com.org.playboard.data.model.Group.canManage] so only owners/admins reach it.
     */
    fun onEditGroupClicked(groupId: String,groupName: String) {
        _uiState.update {
            it.copy(isExpanded = false, renameSheet = RenameSheetState(groupId,groupName))
        }
    }

    fun onRenameInputChanged(input: String) = updateRenameSheet { it.copy(input = input, hasFailed = false) }

    fun onRenameSheetDismissed() {
        _uiState.update { it.copy(renameSheet = null) }
    }

    fun onRenameSubmit() {
        val sheet = _uiState.value.renameSheet ?: return
        if (!sheet.canSubmit) return
        updateRenameSheet { it.copy(isSubmitting = true, hasFailed = false) }
        viewModelScope.launch {
            groupRepository.renameGroup(sheet.groupId, sheet.input)
                .onSuccess { _uiState.update { state -> state.copy(renameSheet = null) } }
                .onFailure { updateRenameSheet { it.copy(isSubmitting = false, hasFailed = true) } }
        }
    }

    /** Applies [transform] to the open rename sheet; a no-op if it's already closed. */
    private inline fun updateRenameSheet(transform: (RenameSheetState) -> RenameSheetState) {
        _uiState.update { state ->
            state.renameSheet?.let { state.copy(renameSheet = transform(it)) } ?: state
        }
    }

    private fun generateInvite(groupId: String) {
        viewModelScope.launch {
            groupRepository.createInvite(groupId)
                .onSuccess { code -> updateInviteSheet { it.copy(isLoading = false, code = code, hasFailed = false) } }
                .onFailure { updateInviteSheet { it.copy(isLoading = false, hasFailed = true) } }
        }
    }

    /** Applies [transform] to the open invite sheet; a no-op if it's already closed. */
    private inline fun updateInviteSheet(transform: (InviteSheetState) -> InviteSheetState) {
        _uiState.update { state ->
            state.inviteSheet?.let { state.copy(inviteSheet = transform(it)) } ?: state
        }
    }

    fun onSheetModeChanged(mode: GroupActionMode) = updateSheet { it.copy(mode = mode, input = "", error = null) }

    fun onSheetInputChanged(input: String) = updateSheet { it.copy(input = input, error = null) }

    fun onSheetDismissed() {
        _uiState.update { it.copy(groupActionSheet = null) }
    }

    fun onSheetSubmit() {
        val sheet = _uiState.value.groupActionSheet ?: return
        if (!sheet.canSubmit) return
        updateSheet { it.copy(isSubmitting = true, error = null) }
        viewModelScope.launch {
            val result = when (sheet.mode) {
                GroupActionMode.CREATE -> groupRepository.createGroup(sheet.input)
                GroupActionMode.JOIN -> groupRepository.joinGroup(sheet.input)
            }
            result
                .onSuccess {
                    // The repository already made the new group active; the mirrored
                    // selection flow updates the header, and each tab reloads for it.
                    _uiState.update { it.copy(groupActionSheet = null) }
                }
                .onFailure { cause ->
                    val error =
                        if (cause is InvalidInviteCodeException) GroupActionError.INVALID_CODE else GroupActionError.NETWORK
                    updateSheet { it.copy(isSubmitting = false, error = error) }
                }
        }
    }

    /** Applies [transform] to the open create/join sheet; a no-op if it's already closed. */
    private inline fun updateSheet(transform: (GroupActionSheetState) -> GroupActionSheetState) {
        _uiState.update { state ->
            state.groupActionSheet?.let { state.copy(groupActionSheet = transform(it)) } ?: state
        }
    }
}
