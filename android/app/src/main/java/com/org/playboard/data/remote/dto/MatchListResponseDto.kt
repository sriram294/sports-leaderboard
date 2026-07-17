package com.org.playboard.data.remote.dto

import kotlinx.serialization.Serializable

/** `GET /groups/{id}/matches` — flat, newest first (client groups by date). */
@Serializable
data class MatchListResponseDto(
    val matches: List<MatchSummaryDto>,
    val nextCursor: String? = null,
)

@Serializable
data class MatchSummaryDto(
    val id: String,
    val playedAt: String,
    val teams: List<MatchTeamDto>,
    val sets: List<MatchSetDto>,
)

@Serializable
data class MatchTeamDto(
    val teamNo: Int,
    val isWinner: Boolean,
    val players: List<MatchPlayerDto>,
)

@Serializable
data class MatchPlayerDto(
    val userId: String,
    val displayName: String,
    val avatarColor: String,
    val photoUrl: String? = null,
    val avatarId: String? = null,
)

@Serializable
data class MatchSetDto(
    val setNo: Int,
    val team1Score: Int,
    val team2Score: Int,
)
