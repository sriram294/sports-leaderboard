package com.org.playboard.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.org.playboard.data.auth.AuthRepository
import com.org.playboard.data.group.GroupRepository
import com.org.playboard.data.model.Group
import com.org.playboard.data.model.SessionState
import com.org.playboard.data.model.UserSession
import com.org.playboard.data.stats.StatsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Profile tab (docs/requirements/05-profile.md): the signed-in user's account
 * info and per-group stats. Stats are scoped to the active group — switching
 * groups via the header re-fetches everything. A match recorded/deleted
 * elsewhere bumps the data revision, so the profile refreshes silently.
 *
 * Viewing *another* player (via a leaderboard tap, req #2) reuses this layout
 * with a different `userId` and no account section — deferred to a follow-on;
 * this ViewModel always resolves the signed-in user's own stats for now.
 */
@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val groupRepository: GroupRepository,
    private val statsRepository: StatsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    private val currentUser = authRepository.sessionState
        .map { (it as? SessionState.SignedIn)?.user }

    init {
        viewModelScope.launch {
            currentUser.collect { user ->
                _uiState.update { it.copy(email = user?.email) }
            }
        }
        // Reload when the signed-in user or the active group changes.
        viewModelScope.launch {
            combine(currentUser, groupRepository.selectedGroup) { user, group -> user to group }
                .distinctUntilChanged { old, new -> old.first?.id == new.first?.id && old.second?.id == new.second?.id }
                .collect { (user, group) -> load(user, group, showLoading = true) }
        }
        // A match recorded/deleted anywhere changes these stats — refresh silently.
        viewModelScope.launch {
            groupRepository.dataRevision.drop(1).collect {
                val user = currentUser.first() ?: return@collect
                val group = groupRepository.selectedGroup.first() ?: return@collect
                load(user, group, showLoading = false)
            }
        }
    }

    fun retry() {
        viewModelScope.launch {
            val user = currentUser.first()
            val group = groupRepository.selectedGroup.first()
            load(user, group, showLoading = true)
        }
    }

    fun onSignOutClicked() {
        viewModelScope.launch { authRepository.signOut() }
    }

    private suspend fun load(user: UserSession?, group: Group?, showLoading: Boolean) {
        if (user == null) return
        if (group == null) {
            _uiState.update {
                it.copy(isLoading = false, noGroup = true, groupName = null, stats = null, hasLoadFailed = false)
            }
            return
        }
        _uiState.update {
            it.copy(isLoading = showLoading, hasLoadFailed = false, noGroup = false, groupName = group.name)
        }
        statsRepository.getPlayerStats(group.id, user.id)
            .onSuccess { stats -> _uiState.update { it.copy(isLoading = false, stats = stats) } }
            .onFailure {
                // Keep stale stats on a silent refresh failure; only show the error
                // screen when there's nothing to show.
                _uiState.update { it.copy(isLoading = false, hasLoadFailed = it.stats == null) }
            }
    }
}
