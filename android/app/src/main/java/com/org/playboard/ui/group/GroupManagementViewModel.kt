package com.org.playboard.ui.group

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.org.playboard.data.auth.AuthRepository
import com.org.playboard.data.group.GroupRepository
import com.org.playboard.data.model.SessionState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Backs the group-management drill-down (docs/requirements — group settings): the groups the
 * signed-in user owns/admins, and per-group member management (remove, role changes,
 * add/invite) + the daily session window. Reloads members on data changes.
 */
@HiltViewModel
class GroupManagementViewModel @Inject constructor(
    authRepository: AuthRepository,
    private val groupRepository: GroupRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(GroupManagementUiState())
    val uiState: StateFlow<GroupManagementUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            authRepository.sessionState.collect { session ->
                _uiState.update { it.copy(currentUserId = (session as? SessionState.SignedIn)?.user?.id) }
            }
        }
        viewModelScope.launch {
            groupRepository.groups.collect { groups ->
                _uiState.update { it.copy(managedGroups = groups.filter { g -> g.canManage }) }
            }
        }
        // A member add/remove/role change bumps the revision — reload the open group's roster.
        viewModelScope.launch {
            groupRepository.dataRevision.drop(1).collect {
                _uiState.value.selectedGroupId?.let { loadMembers(it) }
            }
        }
    }

    fun onSelectGroup(groupId: String) {
        _uiState.update { it.copy(selectedGroupId = groupId, members = emptyList(), actionError = null) }
        loadMembers(groupId)
    }

    /** Returns to the managed-groups list. */
    fun onBackToList() {
        _uiState.update { it.copy(selectedGroupId = null, members = emptyList(), actionError = null) }
    }

    fun retryMembers() {
        _uiState.value.selectedGroupId?.let { loadMembers(it) }
    }

    private fun loadMembers(groupId: String) {
        _uiState.update { it.copy(isLoadingMembers = true, membersFailed = false) }
        viewModelScope.launch {
            groupRepository.getMembers(groupId)
                .onSuccess { roster ->
                    _uiState.update {
                        if (it.selectedGroupId != groupId) it
                        else it.copy(isLoadingMembers = false, members = roster.filterNot { m -> m.isGuest })
                    }
                }
                .onFailure {
                    _uiState.update {
                        if (it.selectedGroupId != groupId) it
                        else it.copy(isLoadingMembers = false, membersFailed = it.members.isEmpty())
                    }
                }
        }
    }

    fun onRemoveMember(userId: String) = runAction("Couldn't remove member. Please try again.") { groupId ->
        groupRepository.removeMember(groupId, userId)
    }

    fun onChangeRole(userId: String, newRole: String) = runAction("Couldn't change role. Please try again.") { groupId ->
        groupRepository.changeMemberRole(groupId, userId, newRole)
    }

    fun onAddMember(email: String, name: String) = runAction("Couldn't add member. Please try again.") { groupId ->
        groupRepository.addMember(groupId, email, name).map { }
    }

    /** Saves ([start]/[end] "HH:mm") or clears (both null) the selected group's session window. */
    fun onSetSession(start: String?, end: String?) = runAction("Couldn't save the session time. Please try again.") { groupId ->
        groupRepository.setSessionTime(groupId, start, end).map { }
    }

    fun onCreateInvite() {
        val groupId = _uiState.value.selectedGroupId ?: return
        _uiState.update { it.copy(busy = true, actionError = null) }
        viewModelScope.launch {
            groupRepository.createInvite(groupId)
                .onSuccess { code -> _uiState.update { it.copy(busy = false, inviteCode = code) } }
                .onFailure { _uiState.update { it.copy(busy = false, actionError = "Couldn't create an invite. Please try again.") } }
        }
    }

    fun onInviteDismissed() {
        _uiState.update { it.copy(inviteCode = null) }
    }

    fun onErrorDismissed() {
        _uiState.update { it.copy(actionError = null) }
    }

    /** Runs a member/session action against the selected group, driving [busy]/[actionError]. */
    private fun runAction(errorMessage: String, action: suspend (groupId: String) -> Result<Unit>) {
        val groupId = _uiState.value.selectedGroupId ?: return
        _uiState.update { it.copy(busy = true, actionError = null) }
        viewModelScope.launch {
            action(groupId)
                .onSuccess { _uiState.update { it.copy(busy = false) } }
                .onFailure { _uiState.update { it.copy(busy = false, actionError = errorMessage) } }
        }
    }
}
