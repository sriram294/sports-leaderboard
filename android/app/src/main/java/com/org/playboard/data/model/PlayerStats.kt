package com.org.playboard.data.model

/**
 * A player's stats within one group (docs/requirements/05-profile.md § Data
 * needed). Scoped to the currently selected group — switching groups re-fetches.
 */
data class PlayerStats(
    val userId: String,
    val displayName: String,
    val photoUrl: String?,
    val avatarId: String?,
    val avatarColor: String,
    val matchesPlayed: Int,
    val wins: Int,
    val losses: Int,
    val pointsFor: Int,
    val pointsAgainst: Int,
    val winRate: Double,
    val currentStreak: Int,
    val bestStreak: Int,
    val bestPartner: BestPartner?,
    /** Last matches involving this player, newest first (≤5). */
    val recentMatches: List<Match>,
) {
    /** Win rate as a whole percentage for display (e.g. `0.5` → `50`). */
    val winRatePercent: Int get() = (winRate * 100).toInt()
}

/** The partner this player wins most with in the group (min-games threshold is server-side). */
data class BestPartner(
    val userId: String,
    val displayName: String,
    val photoUrl: String?,
    val avatarId: String?,
    val avatarColor: String,
    val gamesTogether: Int,
    val winsTogether: Int,
    val winRate: Double,
) {
    val winRatePercent: Int get() = (winRate * 100).toInt()
}
