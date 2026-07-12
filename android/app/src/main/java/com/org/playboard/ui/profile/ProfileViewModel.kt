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
 * Profile tab (docs/requirements/05-profile.md): a player's account info and
 * per-group stats. Stats are scoped to the active group — switching groups via
 * the header re-fetches everything. A match recorded/deleted elsewhere bumps the
 * data revision, so the profile refreshes silently.
 *
 * Serves both the Profile tab (the signed-in user's own stats) and a leaderboard
 * drill-down (another player, req #2): [setViewedUser] picks whose stats to load.
 * A viewed player resolves [ProfileUiState.isOwnProfile] to `false`, which hides
 * the account section; `null` (or the signed-in user's own id) shows own profile.
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

    /** Which player to show — `null` means the signed-in user's own profile. */
    private val _viewedUserId = MutableStateFlow<String?>(null)

    /** Selects whose profile to load; `null` = the signed-in user's own. */
    fun setViewedUser(userId: String?) {
        _viewedUserId.value = userId
    }

    init {
        viewModelScope.launch {
            currentUser.collect { user ->
                _uiState.update { it.copy(email = user?.email) }
            }
        }
        // Reload when the signed-in user, the active group, or the viewed player changes.
        viewModelScope.launch {
            combine(currentUser, groupRepository.selectedGroup, _viewedUserId) { user, group, viewedId ->
                Triple(user, group, viewedId)
            }
                .distinctUntilChanged { old, new ->
                    old.first?.id == new.first?.id && old.second?.id == new.second?.id && old.third == new.third
                }
                .collect { (user, group, viewedId) -> load(user, group, viewedId, showLoading = true) }
        }
        // A match recorded/deleted anywhere changes these stats — refresh silently
        // for whoever is currently shown.
        viewModelScope.launch {
            groupRepository.dataRevision.drop(1).collect {
                val user = currentUser.first() ?: return@collect
                val group = groupRepository.selectedGroup.first() ?: return@collect
                load(user, group, _viewedUserId.value, showLoading = false)
            }
        }
    }

    fun retry() {
        viewModelScope.launch {
            val user = currentUser.first()
            val group = groupRepository.selectedGroup.first()
            load(user, group, _viewedUserId.value, showLoading = true)
        }
    }

    fun onSignOutClicked() {
        viewModelScope.launch { authRepository.signOut() }
    }

    private suspend fun load(user: UserSession?, group: Group?, viewedId: String?, showLoading: Boolean) {
        if (user == null) return
        val targetId = viewedId ?: user.id
        val isOwnProfile = targetId == user.id
        if (group == null) {
            _uiState.update {
                it.copy(
                    isLoading = false, noGroup = true, groupName = null, stats = null,
                    hasLoadFailed = false, isOwnProfile = isOwnProfile,
                )
            }
            return
        }
        _uiState.update {
            it.copy(
                isLoading = showLoading, hasLoadFailed = false, noGroup = false,
                groupName = group.name, isOwnProfile = isOwnProfile,
                // Drop stale stats on a foreground (re)load so a failure can't show a
                // different player's numbers; a silent revision refresh keeps them.
                stats = if (showLoading) null else it.stats,
            )
        }
        statsRepository.getPlayerStats(group.id, targetId)
            .onSuccess { stats -> _uiState.update { it.copy(isLoading = false, stats = stats) } }
            .onFailure {
                // Keep stale stats on a silent refresh failure; only show the error
                // screen when there's nothing to show.
                _uiState.update { it.copy(isLoading = false, hasLoadFailed = it.stats == null) }
            }
    }
}
