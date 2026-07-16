package com.org.playboard.ui.board

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.org.playboard.data.auth.AuthRepository
import com.org.playboard.data.group.GroupRepository
import com.org.playboard.data.group.GroupsLoadState
import com.org.playboard.data.leaderboard.LeaderboardRepository
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
 * Board tab (docs/requirements/02-board-leaderboard.md): shows the leaderboard
 * of the active group. The active group is chosen from the shared group switcher
 * ([com.org.playboard.ui.switcher.GroupSwitcher]) — this ViewModel observes it
 * and (re)loads the podium + rankings whenever it changes or a match is recorded.
 */
@HiltViewModel
class BoardViewModel @Inject constructor(
    private val groupRepository: GroupRepository,
    private val leaderboardRepository: LeaderboardRepository,
    private val authRepository: AuthRepository,
    private val statsRepository: StatsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BoardUiState())
    val uiState: StateFlow<BoardUiState> = _uiState.asStateFlow()

    private val currentUser = authRepository.sessionState.map { (it as? SessionState.SignedIn)?.user }

    init {
        // Follow the active group; also react to the group-list load status so we
        // can tell "loading" from "no groups" from "group fetch failed" while the
        // switcher owns the actual fetch.
        viewModelScope.launch {
            combine(groupRepository.selectedGroup, groupRepository.groupsLoadState) { group, loadState ->
                group to loadState
            }
                .distinctUntilChanged { old, new -> old.first?.id == new.first?.id && old.second == new.second }
                .collect { (group, loadState) -> applySelection(group, loadState) }
        }
        // The form bar is secondary to the rankings, so it loads on its own collector:
        // getPlayerStats runs concurrently with getLeaderboard, and a form failure can
        // never delay or fail the leaderboard. A separate collector also keeps the
        // session's initial null -> user emission from re-fetching the leaderboard.
        viewModelScope.launch {
            combine(currentUser, groupRepository.selectedGroup) { user, group -> user to group }
                .distinctUntilChanged { old, new ->
                    old.first?.id == new.first?.id && old.second?.id == new.second?.id
                }
                .collect { (user, group) -> loadForm(user, group, keepStale = false) }
        }
        // A match recorded on the Add tab (or deleted) changes the leaderboard;
        // re-fetch silently when the shared data revision advances.
        viewModelScope.launch {
            groupRepository.dataRevision.drop(1).collect {
                val group = groupRepository.selectedGroup.first() ?: return@collect
                loadLeaderboard(group, showLoading = false)
                loadForm(currentUser.first(), group, keepStale = true)
            }
        }
    }

    /** Retry path: recover a failed group-list fetch, or reload the leaderboard. */
    fun refresh() {
        viewModelScope.launch {
            val group = groupRepository.selectedGroup.first()
            if (group == null) {
                groupRepository.refreshGroups()
            } else {
                loadLeaderboard(group, showLoading = true)
                loadForm(currentUser.first(), group, keepStale = false)
            }
        }
    }

    /**
     * Pull-to-refresh: silently re-sync the group list (member counts) and this
     * group's leaderboard, driving the pull indicator via [BoardUiState.isRefreshing]
     * rather than the full-screen spinner.
     */
    fun onPullRefresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            groupRepository.refreshGroups(showLoading = false)
            groupRepository.selectedGroup.first()?.let { group ->
                loadLeaderboard(group, showLoading = false)
                loadForm(currentUser.first(), group, keepStale = true)
            }
            _uiState.update { it.copy(isRefreshing = false) }
        }
    }

    fun onSortColumnSelected(column: RankingSortColumn) {
        _uiState.update { it.copy(sortColumn = column) }
    }

    private suspend fun applySelection(group: Group?, loadState: GroupsLoadState) {
        if (group != null) {
            loadLeaderboard(group, showLoading = true)
            return
        }
        // No active group — reflect why (loading / failed / genuinely none).
        _uiState.update {
            it.copy(
                isLoading = loadState == GroupsLoadState.LOADING,
                hasLoadFailed = loadState == GroupsLoadState.FAILED,
                selectedGroup = null,
                rankings = emptyList(),
            )
        }
    }

    private suspend fun loadLeaderboard(group: Group, showLoading: Boolean) {
        _uiState.update { it.copy(isLoading = showLoading, hasLoadFailed = false, selectedGroup = group) }
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
                // Keep a stale list on a silent refresh failure; only show the error
                // screen when there's nothing to show.
                _uiState.update {
                    it.copy(isLoading = false, selectedGroup = group, hasLoadFailed = it.rankings.isEmpty())
                }
            }
    }

    /**
     * Loads the signed-in user's last-5 results for the pinned form bar. Secondary to
     * the leaderboard: a failure never sets [BoardUiState.hasLoadFailed] — the bar
     * keeps stale pills on a silent refresh ([keepStale]), or just stays hidden.
     */
    private suspend fun loadForm(user: UserSession?, group: Group?, keepStale: Boolean) {
        if (user == null || group == null) {
            _uiState.update { it.copy(recentForm = emptyList()) }
            return
        }
        // Drop the previous group's pills up front so a slow or failed fetch can't
        // leave form from the group the user just left on screen.
        if (!keepStale) _uiState.update { it.copy(recentForm = emptyList()) }
        statsRepository.getPlayerStats(group.id, user.id).onSuccess { stats ->
            // A fetch for a group the user has since left must not overwrite.
            if (_uiState.value.selectedGroup?.id != group.id) return@onSuccess
            _uiState.update { it.copy(recentForm = stats.recentMatches.mapNotNull { m -> m.isWinFor(user.id) }) }
        }
        // No onFailure branch — the form degrades silently (mirrors ProfileViewModel.load).
    }
}
