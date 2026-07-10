package com.org.playboard.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * One row of `GET /groups/{groupId}/leaderboard` — server-sorted by win rate
 * desc, then wins desc (docs/backend/api-contracts.md § Leaderboard).
 */
@Serializable
data class LeaderboardEntryDto(
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
)
