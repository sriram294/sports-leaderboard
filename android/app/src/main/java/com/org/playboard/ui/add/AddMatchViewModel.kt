package com.org.playboard.ui.add

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.org.playboard.data.auth.AuthRepository
import com.org.playboard.data.group.GroupRepository
import com.org.playboard.data.match.MatchRepository
import com.org.playboard.data.match.RecordMatchException
import com.org.playboard.data.model.Group
import com.org.playboard.data.model.SessionState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Add Match form (docs/requirements/04-add-match.md): loads the active group's
 * roster, lets the user build two teams, score sets, and pick the winner, then
 * records the match. The form is scoped to the active group and resets when the
 * group changes or a match is recorded.
 */
@HiltViewModel
class AddMatchViewModel @Inject constructor(
    authRepository: AuthRepository,
    private val groupRepository: GroupRepository,
    private val matchRepository: MatchRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddMatchUiState())
    val uiState: StateFlow<AddMatchUiState> = _uiState.asStateFlow()

    /**
     * Emits once per successfully recorded match — the screen navigates away on
     * it. `extraBufferCapacity = 1` so the event isn't lost if the collector
     * isn't attached at the exact moment of emit (replay stays 0, so it never
     * re-delivers to a fresh subscriber).
     */
    private val _recorded = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val recorded: SharedFlow<Unit> = _recorded.asSharedFlow()

    init {
        viewModelScope.launch {
            authRepository.sessionState.collect { session ->
                _uiState.update { it.copy(recorder = (session as? SessionState.SignedIn)?.user) }
            }
        }
        viewModelScope.launch {
            groupRepository.selectedGroup.distinctUntilChangedBy { it?.id }.collect { group ->
                if (group == null) {
                    _uiState.update {
                        it.copy(isLoading = false, noGroup = true, groupId = null, groupName = null)
                    }
                } else {
                    loadRoster(group)
                }
            }
        }
    }

    fun retry() {
        viewModelScope.launch {
            val group = groupRepository.selectedGroup.first() ?: return@launch
            loadRoster(group)
        }
    }

    /** Opens the "Select Player" sheet for [teamNo], unless that team is already full. */
    fun onEmptySlotClicked(teamNo: Int) {
        val team = if (teamNo == 1) _uiState.value.team1 else _uiState.value.team2
        if (team.size >= AddMatchUiState.TEAM_SIZE) return
        _uiState.update { it.copy(playerPickerTeam = teamNo) }
    }

    /** Assigns the picked player to the team the sheet was opened for, then closes it. */
    fun onPlayerPicked(userId: String) {
        _uiState.update { state ->
            val teamNo = state.playerPickerTeam
            val next = when {
                teamNo == null || userId in state.assignedIds -> state
                teamNo == 1 && state.team1.size < AddMatchUiState.TEAM_SIZE -> state.copy(team1 = state.team1 + userId)
                teamNo == 2 && state.team2.size < AddMatchUiState.TEAM_SIZE -> state.copy(team2 = state.team2 + userId)
                else -> state
            }
            next.copy(playerPickerTeam = null, submitError = null)
        }
    }

    fun onPlayerPickerDismissed() {
        _uiState.update { it.copy(playerPickerTeam = null) }
    }

    /** Removes a player from whichever team they're on (tapping a filled slot). */
    fun onRemovePlayer(userId: String) {
        _uiState.update {
            it.copy(team1 = it.team1 - userId, team2 = it.team2 - userId, submitError = null)
        }
    }

    fun onSetScoreChanged(index: Int, teamNo: Int, value: String) {
        val digits = value.filter { it.isDigit() }.take(2)
        _uiState.update { state ->
            if (index !in state.sets.indices) return@update state
            val sets = state.sets.toMutableList()
            sets[index] = if (teamNo == 1) sets[index].copy(team1 = digits) else sets[index].copy(team2 = digits)
            state.copy(sets = sets, submitError = null)
        }
    }

    fun onAddSet() {
        _uiState.update { it.copy(sets = it.sets + SetScoreInput()) }
    }

    fun onRemoveSet(index: Int) {
        _uiState.update { state ->
            if (state.sets.size <= 1 || index !in state.sets.indices) return@update state
            state.copy(sets = state.sets.filterIndexed { i, _ -> i != index }, submitError = null)
        }
    }

    fun onWinnerSelected(teamNo: Int) {
        _uiState.update { it.copy(winnerOverride = teamNo, submitError = null) }
    }

    fun onRecord() {
        val state = _uiState.value
        val groupId = state.groupId ?: return
        val winner = state.effectiveWinner ?: return
        if (!state.canRecord) return
        val sets = state.parsedSets.filterNotNull()
        _uiState.update { it.copy(isSubmitting = true, submitError = null) }
        viewModelScope.launch {
            matchRepository.recordMatch(groupId, state.team1, state.team2, sets, winner)
                .onSuccess {
                    resetForm()
                    _recorded.emit(Unit)
                }
                .onFailure { cause ->
                    val error = when (cause) {
                        is RecordMatchException.InvalidScores -> RecordMatchError.INVALID_SCORES
                        is RecordMatchException.InvalidTeams -> RecordMatchError.INVALID_TEAMS
                        else -> RecordMatchError.NETWORK
                    }
                    _uiState.update { it.copy(isSubmitting = false, submitError = error) }
                }
        }
    }

    private suspend fun loadRoster(group: Group) {
        _uiState.update {
            it.copy(
                isLoading = true,
                hasLoadFailed = false,
                noGroup = false,
                groupId = group.id,
                groupName = group.name,
                roster = emptyList(),
                team1 = emptyList(),
                team2 = emptyList(),
                sets = listOf(SetScoreInput()),
                winnerOverride = null,
                submitError = null,
                playerPickerTeam = null,
            )
        }
        groupRepository.getMembers(group.id)
            .onSuccess { members -> _uiState.update { it.copy(isLoading = false, roster = members) } }
            .onFailure { _uiState.update { it.copy(isLoading = false, hasLoadFailed = true) } }
    }

    private fun resetForm() {
        _uiState.update {
            it.copy(
                team1 = emptyList(),
                team2 = emptyList(),
                sets = listOf(SetScoreInput()),
                winnerOverride = null,
                isSubmitting = false,
                submitError = null,
                playerPickerTeam = null,
            )
        }
    }
}
