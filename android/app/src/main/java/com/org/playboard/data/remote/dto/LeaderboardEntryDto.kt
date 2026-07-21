package com.org.playboard.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * One row of `GET /groups/{groupId}/leaderboard` — server-sorted by rating desc,
 * then points difference desc, then wins desc
 * (docs/backend/api-contracts.md § Leaderboard).
 */
@Serializable
data class LeaderboardEntryDto(
    val rank: Int,
    val userId: String,
    val displayName: String,
    val photoUrl: String?,
    val avatarId: String? = null,
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
    // Wilson score lower bound on the win rate, 0-100. Nullable rather than defaulted to
    // 0.0 — unlike the fields above, 0.0 is a *legitimate* rating for a winless player, so
    // a default would make "backend predates ratings" indistinguishable from "has never
    // won". The UI falls back to showing win% while this is null.
    val rating: Double? = null,
    // Below the group's minGamesToRank: listed, but not ranked.
    val provisional: Boolean = false,
)
