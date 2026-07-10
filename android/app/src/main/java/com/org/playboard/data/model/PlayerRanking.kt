package com.org.playboard.data.model

/**
 * One leaderboard row for a group. [rank] is the server-assigned position
 * (win rate desc, then wins desc) and stays fixed even when the UI re-sorts
 * the table by another column.
 */
data class PlayerRanking(
    val rank: Int,
    val userId: String,
    val displayName: String,
    val photoUrl: String?,
    val avatarColor: String,
    val gamesPlayed: Int,
    val wins: Int,
    val losses: Int,
    val pointsFor: Int,
    val winRate: Double,
) {
    /** Win rate as a whole percentage for display (e.g. `0.83` → `83`). */
    val winRatePercent: Int get() = (winRate * 100).toInt()
}
