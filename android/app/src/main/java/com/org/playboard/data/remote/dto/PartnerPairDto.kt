package com.org.playboard.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * `GET /groups/{groupId}/stats/partners` — one pair of players who have
 * partnered together at least once in the group, group-wide (not scoped to
 * one player).
 */
@Serializable
data class PartnerPairDto(
    val player1Id: String,
    val player1DisplayName: String,
    val player1AvatarId: String? = null,
    val player1AvatarColor: String,
    val player2Id: String,
    val player2DisplayName: String,
    val player2AvatarId: String? = null,
    val player2AvatarColor: String,
    val gamesTogether: Int,
    val winsTogether: Int,
    val winRate: Double,
)
