package com.org.playboard.data.model

/**
 * One pair of players in a group who have partnered together at least once,
 * group-wide (docs/requirements/06-stats.md). Fetched on demand — only when
 * the Stats "Partners" card is expanded.
 */
data class PartnerPairing(
    val player1Id: String,
    val player1DisplayName: String,
    val player1AvatarId: String?,
    val player1AvatarColor: String,
    val player2Id: String,
    val player2DisplayName: String,
    val player2AvatarId: String?,
    val player2AvatarColor: String,
    val gamesTogether: Int,
    val winsTogether: Int,
    val winRate: Double,
) {
    val winRatePercent: Int get() = (winRate * 100).toInt()
}
