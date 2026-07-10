package com.org.playboard.ui.profile

import com.org.playboard.data.model.Match
import com.org.playboard.data.model.MatchSet
import com.org.playboard.data.model.PlayerStats
import java.time.Instant

/** Immutable state for the Profile tab (docs/requirements/05-profile.md). */
data class ProfileUiState(
    val isLoading: Boolean = true,
    val hasLoadFailed: Boolean = false,
    val noGroup: Boolean = false,
    val groupName: String? = null,
    /** Signed-in user's email — shown in the account row (own profile only). */
    val email: String? = null,
    /**
     * Whether this is the signed-in user's own profile. Own profile shows the
     * account section (email + Sign out); a viewed player's does not (req #2).
     */
    val isOwnProfile: Boolean = true,
    val stats: PlayerStats? = null,
) {
    /** Recent matches rendered relative to the viewed player (partner vs opponents). */
    val recentMatches: List<RecentMatchRow>
        get() = stats?.let { s -> s.recentMatches.map { it.toRow(s.userId) } } ?: emptyList()
}

/**
 * One "Recent Matches" entry, framed from the viewed player's perspective:
 * who they played *with* vs *against*, and whether they won.
 */
data class RecentMatchRow(
    val matchId: String,
    val playedAt: Instant,
    val isWin: Boolean,
    val partnerNames: String,
    val opponentNames: String,
    val sets: List<MatchSet>,
)

private fun Match.toRow(userId: String): RecentMatchRow {
    val myTeam = teams.firstOrNull { team -> team.players.any { it.userId == userId } }
    val opponents = teams.firstOrNull { it.teamNo != myTeam?.teamNo }
    val partners = myTeam?.players.orEmpty().filter { it.userId != userId }
    return RecentMatchRow(
        matchId = id,
        playedAt = playedAt,
        isWin = myTeam?.isWinner == true,
        partnerNames = partners.joinToString(" & ") { it.displayName },
        opponentNames = opponents?.players.orEmpty().joinToString(" & ") { it.displayName },
        sets = sets,
    )
}
