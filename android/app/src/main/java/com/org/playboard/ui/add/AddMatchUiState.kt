package com.org.playboard.ui.add

import com.org.playboard.data.model.Member
import com.org.playboard.data.model.UserSession

/** A single set's raw score inputs — kept as strings so partial entry is representable. */
data class SetScoreInput(val team1: String = "", val team2: String = "")

/** Why a record attempt failed, mapped to a user-facing message in the form. */
enum class RecordMatchError { INVALID_SCORES, INVALID_TEAMS, NETWORK }

/** Immutable state for the Add Match form (docs/requirements/04-add-match.md). */
data class AddMatchUiState(
    val isLoading: Boolean = true,
    val hasLoadFailed: Boolean = false,
    val noGroup: Boolean = false,
    val groupId: String? = null,
    val groupName: String? = null,
    val recorder: UserSession? = null,
    val roster: List<Member> = emptyList(),
    val team1: List<String> = emptyList(),
    val team2: List<String> = emptyList(),
    val sets: List<SetScoreInput> = listOf(SetScoreInput()),
    val winnerOverride: Int? = null,
    val isSubmitting: Boolean = false,
    val submitError: RecordMatchError? = null,
    /** Team (1 or 2) the "Select Player" sheet is currently picking for; `null` = closed. */
    val playerPickerTeam: Int? = null,
    /** Id of the match being edited; `null` = recording a new match (create mode). */
    val editingMatchId: String? = null,
) {
    val isEditing: Boolean get() = editingMatchId != null

    val assignedIds: Set<String> get() = (team1 + team2).toSet()

    /** Roster members not yet on a team — the choices shown in the picker. */
    val availablePlayers: List<Member> get() = roster.filter { it.id !in assignedIds }

    val teamsComplete: Boolean get() = team1.size == TEAM_SIZE && team2.size == TEAM_SIZE

    /** Parsed (team1, team2) per set; a `null` element is a blank/unparseable row. */
    val parsedSets: List<Pair<Int, Int>?> get() = sets.map { it.parsed() }

    /** Every set fully entered with two non-negative integers and no tie. */
    val setsValid: Boolean get() = sets.isNotEmpty() && parsedSets.all { it != null && it.first != it.second }

    /** Winner implied by sets won, or `null` if undetermined / tied on set count. */
    val autoWinner: Int? get() {
        if (!setsValid) return null
        val pairs = parsedSets.filterNotNull()
        val t1 = pairs.count { it.first > it.second }
        val t2 = pairs.count { it.second > it.first }
        return when {
            t1 > t2 -> 1
            t2 > t1 -> 2
            else -> null
        }
    }

    /** A manual pick overrides the auto-derived winner (requirement #3). */
    val effectiveWinner: Int? get() = winnerOverride ?: autoWinner

    val canRecord: Boolean
        get() = teamsComplete && setsValid && effectiveWinner != null && !isSubmitting

    fun member(id: String): Member? = roster.firstOrNull { it.id == id }

    fun teamPlayers(teamNo: Int): List<Member> =
        (if (teamNo == 1) team1 else team2).mapNotNull(::member)

    companion object {
        // Only sport is badminton_doubles (2 per team); no sport picker yet.
        const val TEAM_SIZE = 2
    }
}

private fun SetScoreInput.parsed(): Pair<Int, Int>? {
    val a = team1.toIntOrNull() ?: return null
    val b = team2.toIntOrNull() ?: return null
    if (a < 0 || b < 0) return null
    return a to b
}
