package com.org.playboard.ui.matches

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.org.playboard.data.auth.AuthRepository
import com.org.playboard.data.group.GroupRepository
import com.org.playboard.data.match.MatchRepository
import com.org.playboard.data.model.Group
import com.org.playboard.data.model.SessionState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Matches tab (docs/requirements/03-matches.md): the active group's match log,
 * newest first. Cards expand in place to fetch full detail (breakdown + history);
 * matches can be deleted (with confirmation) by the recorder or a moderator.
 */
@HiltViewModel
class MatchesViewModel @Inject constructor(
    authRepository: AuthRepository,
    private val groupRepository: GroupRepository,
    private val matchRepository: MatchRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MatchesUiState())
    val uiState: StateFlow<MatchesUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            authRepository.sessionState.collect { session ->
                _uiState.update { it.copy(currentUserId = (session as? SessionState.SignedIn)?.user?.id) }
            }
        }
        viewModelScope.launch {
            groupRepository.selectedGroup.distinctUntilChangedBy { it?.id }.collect { group ->
                if (group == null) {
                    _uiState.update {
                        it.copy(isLoading = false, noGroup = true, groupId = null, groupName = null, matches = emptyList())
                    }
                } else {
                    loadMatches(group, showLoading = true)
                }
            }
        }
        // A match recorded on the Add tab (or deleted) bumps the revision — refresh
        // silently so the list stays current without a spinner flash.
        viewModelScope.launch {
            groupRepository.dataRevision.drop(1).collect {
                val group = groupRepository.selectedGroup.first() ?: return@collect
                loadMatches(group, showLoading = false)
            }
        }
    }

    fun retry() {
        viewModelScope.launch {
            val group = groupRepository.selectedGroup.first() ?: return@launch
            loadMatches(group, showLoading = true)
        }
    }

    /** Expands a card (fetching its detail) or collapses it if already open. */
    fun onMatchClicked(matchId: String) {
        val state = _uiState.value
        if (state.expandedId == matchId) {
            _uiState.update { it.copy(expandedId = null, detail = null, isDetailLoading = false, detailFailed = false) }
            return
        }
        _uiState.update { it.copy(expandedId = matchId, detail = null, isDetailLoading = true, detailFailed = false) }
        val groupId = state.groupId ?: return
        viewModelScope.launch {
            matchRepository.getMatchDetail(groupId, matchId)
                .onSuccess { detail ->
                    _uiState.update { if (it.expandedId == matchId) it.copy(isDetailLoading = false, detail = detail) else it }
                }
                .onFailure {
                    _uiState.update { if (it.expandedId == matchId) it.copy(isDetailLoading = false, detailFailed = true) else it }
                }
        }
    }

    fun onDeleteClicked(matchId: String) {
        _uiState.update { it.copy(deleteTargetId = matchId) }
    }

    fun onDeleteDismissed() {
        if (_uiState.value.isDeleting) return
        _uiState.update { it.copy(deleteTargetId = null) }
    }

    fun onDeleteConfirmed() {
        val state = _uiState.value
        val groupId = state.groupId ?: return
        val matchId = state.deleteTargetId ?: return
        _uiState.update { it.copy(isDeleting = true) }
        viewModelScope.launch {
            matchRepository.deleteMatch(groupId, matchId)
                .onSuccess {
                    // Optimistically drop it; the dataRevision bump also silently reloads.
                    _uiState.update {
                        it.copy(
                            isDeleting = false,
                            deleteTargetId = null,
                            matches = it.matches.filterNot { m -> m.id == matchId },
                            expandedId = if (it.expandedId == matchId) null else it.expandedId,
                            detail = if (it.expandedId == matchId) null else it.detail,
                        )
                    }
                }
                .onFailure { _uiState.update { it.copy(isDeleting = false, deleteTargetId = null) } }
        }
    }

    private suspend fun loadMatches(group: Group, showLoading: Boolean) {
        _uiState.update {
            it.copy(
                isLoading = showLoading,
                hasLoadFailed = false,
                noGroup = false,
                groupId = group.id,
                groupName = group.name,
                canModerate = group.myRole == "owner" || group.myRole == "admin",
            )
        }
        matchRepository.getMatches(group.id)
            .onSuccess { matches -> _uiState.update { it.copy(isLoading = false, matches = matches) } }
            .onFailure {
                // Keep a stale list on a silent refresh failure; only show the error
                // screen when there's nothing to show.
                _uiState.update { it.copy(isLoading = false, hasLoadFailed = it.matches.isEmpty()) }
            }
    }
}
