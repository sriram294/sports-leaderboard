package com.org.playboard.ui.board

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.org.playboard.data.group.GroupRepository
import com.org.playboard.data.group.GroupsLoadState
import com.org.playboard.data.leaderboard.LeaderboardRepository
import com.org.playboard.data.model.Group
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
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
) : ViewModel() {

    private val _uiState = MutableStateFlow(BoardUiState())
    val uiState: StateFlow<BoardUiState> = _uiState.asStateFlow()

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
        // A match recorded on the Add tab (or deleted) changes the leaderboard;
        // re-fetch silently when the shared data revision advances.
        viewModelScope.launch {
            groupRepository.dataRevision.drop(1).collect {
                val group = groupRepository.selectedGroup.first() ?: return@collect
                loadLeaderboard(group, showLoading = false)
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
            groupRepository.selectedGroup.first()?.let { loadLeaderboard(it, showLoading = false) }
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
}
