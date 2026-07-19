package com.org.playboard.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.org.playboard.data.group.GroupRepository
import com.org.playboard.data.group.GroupsLoadState
import com.org.playboard.data.leaderboard.LeaderboardRepository
import com.org.playboard.data.match.MatchRepository
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
 * Stats/Insights tab (docs/requirements/06-stats.md): a group-level analytics
 * dashboard scoped to the active group. Follows [com.org.playboard.ui.board.BoardViewModel]:
 * observes the shared active group (+ load status) and reloads on a match change.
 * All sections derive from existing endpoints — the leaderboard (all-time records)
 * plus the recent match window (partnership / form / biggest win) — computed by the
 * pure functions in StatsComputations, so there's no new backend.
 */
@HiltViewModel
class StatsViewModel @Inject constructor(
    private val groupRepository: GroupRepository,
    private val leaderboardRepository: LeaderboardRepository,
    private val matchRepository: MatchRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(StatsUiState())
    val uiState: StateFlow<StatsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(groupRepository.selectedGroup, groupRepository.groupsLoadState) { group, loadState ->
                group to loadState
            }
                .distinctUntilChanged { old, new -> old.first?.id == new.first?.id && old.second == new.second }
                .collect { (group, loadState) -> applySelection(group, loadState) }
        }
        // A match recorded/edited/deleted changes every section — reload silently.
        viewModelScope.launch {
            groupRepository.dataRevision.drop(1).collect {
                val group = groupRepository.selectedGroup.first() ?: return@collect
                load(group, showLoading = false)
            }
        }
    }

    /** Retry path: recover a failed group-list fetch, or reload the insights. */
    fun retry() {
        viewModelScope.launch {
            val group = groupRepository.selectedGroup.first()
            if (group == null) groupRepository.refreshGroups() else load(group, showLoading = true)
        }
    }

    /** Pull-to-refresh: re-sync the group list + this group's insights, spinner via [StatsUiState.isRefreshing]. */
    fun onPullRefresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            groupRepository.refreshGroups(showLoading = false)
            groupRepository.selectedGroup.first()?.let { load(it, showLoading = false) }
            _uiState.update { it.copy(isRefreshing = false) }
        }
    }

    private suspend fun applySelection(group: Group?, loadState: GroupsLoadState) {
        if (group != null) {
            load(group, showLoading = true)
            return
        }
        // No active group — reflect why (loading / failed / genuinely none).
        _uiState.update {
            it.copy(
                isLoading = loadState == GroupsLoadState.LOADING,
                hasLoadFailed = loadState == GroupsLoadState.FAILED,
                noGroup = loadState == GroupsLoadState.LOADED,
                groupName = null,
                hasMatches = false,
                records = null,
                bestPartnership = null,
                recentForm = emptyList(),
                biggestWin = null,
            )
        }
    }

    private suspend fun load(group: Group, showLoading: Boolean) {
        _uiState.update {
            it.copy(isLoading = showLoading, hasLoadFailed = false, noGroup = false, groupName = group.name)
        }
        val rankings = leaderboardRepository.getLeaderboard(group.id).getOrElse {
            _uiState.update { s -> s.copy(isLoading = false, hasLoadFailed = s.records == null) }
            return
        }
        val matches = matchRepository.getMatches(group.id).getOrElse {
            _uiState.update { s -> s.copy(isLoading = false, hasLoadFailed = s.records == null) }
            return
        }.matches
        _uiState.update {
            it.copy(
                isLoading = false,
                hasLoadFailed = false,
                groupName = group.name,
                hasMatches = group.matchCount > 0,
                records = computeRecords(rankings, group.matchCount),
                bestPartnership = computeBestPartnership(matches),
                recentForm = computeRecentForm(matches, rankings),
                biggestWin = computeBiggestWin(matches),
            )
        }
    }
}
