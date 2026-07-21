package com.org.playboard.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * `GET /groups/{groupId}/members/{userId}/stats` — a player's stats within one
 * group (docs/backend/api-contracts.md § Leaderboard & Player Stats). Backs the
 * Profile tab (own stats) and, later, tapping a player from the leaderboard.
 */
@Serializable
data class PlayerStatsDto(
    val userId: String,
    val displayName: String,
    val photoUrl: String? = null,
    val avatarId: String? = null,
    val avatarColor: String,
    val matchesPlayed: Int,
    val wins: Int,
    val losses: Int,
    val pointsFor: Int,
    val pointsAgainst: Int,
    val winRate: Double,
    val currentStreak: Int,
    val bestStreak: Int,
    /** Null until the player has completed a match with a teammate. */
    val bestPartner: BestPartnerDto? = null,
    /** MatchSummaryDto, newest first, capped at 5 server-side. */
    val recentMatches: List<MatchSummaryDto> = emptyList(),
    /** Months this player topped the group, newest first. Empty on a pre-trophy backend. */
    val trophies: List<MonthlyTrophyDto> = emptyList(),
)

@Serializable
data class BestPartnerDto(
    val userId: String,
    val displayName: String,
    val photoUrl: String? = null,
    val avatarId: String? = null,
    val avatarColor: String,
    val gamesTogether: Int,
    val winsTogether: Int,
    val winRate: Double,
)
