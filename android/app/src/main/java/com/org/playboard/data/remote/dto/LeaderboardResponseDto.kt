package com.org.playboard.data.remote.dto

import kotlinx.serialization.Serializable

/** Envelope of `GET /groups/{groupId}/leaderboard`. */
@Serializable
data class LeaderboardResponseDto(
    val rankings: List<LeaderboardEntryDto>,
)
