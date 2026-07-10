package com.org.playboard.ui.board

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.org.playboard.data.auth.AuthRepository
import com.org.playboard.data.group.GroupRepository
import com.org.playboard.data.leaderboard.LeaderboardRepository
import com.org.playboard.data.model.SessionState
import com.org.playboard.data.remote.InvalidInviteCodeException
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Board tab (docs/requirements/02-board-leaderboard.md): loads the user's
 * groups, then the leaderboard of the active group; switching groups reloads
 * podium + rankings.
 */
@HiltViewModel
class BoardViewModel @Inject constructor(
    authRepository: AuthRepository,
    private val groupRepository: GroupRepository,
    private val leaderboardRepository: LeaderboardRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BoardUiState())
    val uiState: StateFlow<BoardUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            authRepository.sessionState.collect { session ->
                _uiState.update { it.copy(currentUser = (session as? SessionState.SignedIn)?.user) }
            }
        }
        // A match recorded on the Add tab changes the leaderboard; re-fetch when
        // the shared data revision advances (drop(1) skips the initial value).
        viewModelScope.launch {
            groupRepository.dataRevision.drop(1).collect { refresh() }
        }
        refresh()
    }

    /** Full reload — groups then the active group's leaderboard. Also the retry path. */
    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, hasLoadFailed = false) }
            groupRepository.refreshGroups()
                .onSuccess { groups ->
                    _uiState.update { it.copy(groups = groups) }
                    loadLeaderboardForSelection()
                }
                .onFailure { _uiState.update { it.copy(isLoading = false, hasLoadFailed = true) } }
        }
    }

    fun onGroupSwitcherToggled() {
        _uiState.update { it.copy(isGroupSwitcherExpanded = !it.isGroupSwitcherExpanded) }
    }

    fun onGroupSelected(groupId: String) {
        groupRepository.selectGroup(groupId)
        _uiState.update { it.copy(isGroupSwitcherExpanded = false, isLoading = true, hasLoadFailed = false) }
        viewModelScope.launch { loadLeaderboardForSelection() }
    }

    fun onSortColumnSelected(column: RankingSortColumn) {
        _uiState.update { it.copy(sortColumn = column) }
    }

    /** Opens the create/join sheet (and collapses the group switcher behind it). */
    fun onCreateOrJoinGroupClicked() {
        _uiState.update { it.copy(isGroupSwitcherExpanded = false, groupActionSheet = GroupActionSheetState()) }
    }

    /**
     * Opens the invite sheet for the active group and starts generating a code.
     * No-op if there's no active group; the entry point is gated on
     * [com.org.playboard.data.model.Group.canInvite] so only owners/admins reach it.
     */
    fun onInvitePlayersClicked() {
        val group = _uiState.value.selectedGroup ?: return
        _uiState.update {
            it.copy(isGroupSwitcherExpanded = false, inviteSheet = InviteSheetState(groupName = group.name))
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
                    // The repository has already made the new group active; reload the
                    // board for it and sync the switcher's group list.
                    _uiState.update {
                        it.copy(
                            groupActionSheet = null,
                            groups = groupRepository.groups.value,
                            isLoading = true,
                            hasLoadFailed = false,
                        )
                    }
                    loadLeaderboardForSelection()
                }
                .onFailure { cause ->
                    val error =
                        if (cause is InvalidInviteCodeException) GroupActionError.INVALID_CODE else GroupActionError.NETWORK
                    updateSheet { it.copy(isSubmitting = false, error = error) }
                }
        }
    }

    /** Applies [transform] to the open sheet; a no-op if it's already closed. */
    private inline fun updateSheet(transform: (GroupActionSheetState) -> GroupActionSheetState) {
        _uiState.update { state ->
            state.groupActionSheet?.let { state.copy(groupActionSheet = transform(it)) } ?: state
        }
    }

    private suspend fun loadLeaderboardForSelection() {
        val group = groupRepository.selectedGroup.first()
        if (group == null) {
            // User belongs to no groups yet — empty state, nothing to fetch.
            _uiState.update { it.copy(isLoading = false, selectedGroup = null, rankings = emptyList()) }
            return
        }
        leaderboardRepository.getLeaderboard(group.id)
            .onSuccess { rankings ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        selectedGroup = group,
                        rankings = rankings,
                        sortColumn = RankingSortColumn.WIN_RATE,
                    )
                }
            }
            .onFailure {
                _uiState.update { it.copy(isLoading = false, selectedGroup = group, hasLoadFailed = true) }
            }
    }
}
