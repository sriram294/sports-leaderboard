package com.org.playboard.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * One row of `GET /groups/{groupId}/leaderboard` — server-sorted by win rate
 * desc, then points difference desc, then wins desc
 * (docs/backend/api-contracts.md § Leaderboard).
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
    // Defaulted so a pre-points-difference backend's JSON still deserializes during
    // rollout (diff then reads as pointsFor, until the deploy lands).
    val pointsAgainst: Int = 0,
    val winRate: Double,
    // Signed: positive = current win streak, negative = current loss streak.
    // Defaulted so a pre-streak backend's JSON still deserializes during rollout.
    val currentStreak: Int = 0,
    val bestStreak: Int = 0,
)
