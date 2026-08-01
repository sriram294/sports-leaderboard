package com.org.playboard.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.org.playboard.data.group.GroupRepository
import com.org.playboard.data.group.GroupsLoadState
import com.org.playboard.data.leaderboard.LeaderboardRepository
import com.org.playboard.data.match.MatchRepository
import com.org.playboard.data.model.Group
import com.org.playboard.data.stats.StatsRepository
import com.org.playboard.data.trophy.TrophyRepository
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
 * Records derive from the leaderboard (all-time); biggest win from the recent match
 * window — both computed by the pure functions in StatsComputations. The "Partners"
 * card is separate: it's fetched from its own endpoint only once the user expands it,
 * not eagerly with the rest of the page.
 */
@HiltViewModel
class StatsViewModel @Inject constructor(
    private val groupRepository: GroupRepository,
    private val leaderboardRepository: LeaderboardRepository,
    private val matchRepository: MatchRepository,
    private val trophyRepository: TrophyRepository,
    private val statsRepository: StatsRepository,
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
                // A new match also changes partner counts — refresh an open card too.
                if (_uiState.value.partnersExpanded) fetchPartnerPairs(group.id)
            }
        }
    }

    /**
     * Expands the "Partners" card (fetching the full list, every time — same
     * always-refetch-on-expand behavior as MatchesViewModel's match detail) or
     * collapses it if already open.
     */
    fun onPartnersToggled() {
        val state = _uiState.value
        if (state.partnersExpanded) {
            _uiState.update { it.copy(partnersExpanded = false) }
            return
        }
        val groupId = state.groupId ?: return
        _uiState.update { it.copy(partnersExpanded = true, isPartnersLoading = true, partnersLoadFailed = false) }
        viewModelScope.launch { fetchPartnerPairs(groupId) }
    }

    private suspend fun fetchPartnerPairs(groupId: String) {
        statsRepository.getPartnerPairs(groupId)
            .onSuccess { pairs ->
                _uiState.update { if (it.partnersExpanded) it.copy(isPartnersLoading = false, partnerPairs = pairs) else it }
            }
            .onFailure {
                _uiState.update { if (it.partnersExpanded) it.copy(isPartnersLoading = false, partnersLoadFailed = true) else it }
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
                groupId = null,
                hasMatches = false,
                records = null,
                biggestWin = null,
                monthlyWinners = emptyList(),
                partnersExpanded = false,
                partnerPairs = emptyList(),
                isPartnersLoading = false,
                partnersLoadFailed = false,
            )
        }
    }

    private suspend fun load(group: Group, showLoading: Boolean) {
        _uiState.update {
            it.copy(
                isLoading = showLoading, hasLoadFailed = false, noGroup = false,
                groupName = group.name, groupId = group.id,
                // Collapse the partner list too — it may belong to a different group.
                partnersExpanded = if (showLoading) false else it.partnersExpanded,
                partnerPairs = if (showLoading) emptyList() else it.partnerPairs,
                isPartnersLoading = if (showLoading) false else it.isPartnersLoading,
                partnersLoadFailed = if (showLoading) false else it.partnersLoadFailed,
            )
        }
        val rankings = leaderboardRepository.getLeaderboard(group.id).getOrElse {
            _uiState.update { s -> s.copy(isLoading = false, hasLoadFailed = s.records == null) }
            return
        }.rankings
        val matches = matchRepository.getMatches(group.id).getOrElse {
            _uiState.update { s -> s.copy(isLoading = false, hasLoadFailed = s.records == null) }
            return
        }.matches
        // Trophies are decoration on a page whose substance is the sections above, so a
        // failure here degrades to an absent card rather than blanking the whole screen —
        // the same treatment Profile gives its attendance calendar.
        val monthlyWinners = trophyRepository.getGroupTrophies(group.id).getOrDefault(emptyList())
        _uiState.update {
            it.copy(
                isLoading = false,
                hasLoadFailed = false,
                groupName = group.name,
                hasMatches = group.matchCount > 0,
                records = computeRecords(rankings, group.matchCount),
                biggestWin = computeBiggestWin(matches),
                monthlyWinners = monthlyWinners,
            )
        }
    }
}
