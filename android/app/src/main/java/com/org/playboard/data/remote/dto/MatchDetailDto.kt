package com.org.playboard.data.remote.dto

import kotlinx.serialization.Serializable

/** `GET /groups/{id}/matches/{matchId}` — summary fields plus recorder + audit log. */
@Serializable
data class MatchDetailDto(
    val id: String,
    val playedAt: String,
    val teams: List<MatchTeamDto>,
    val sets: List<MatchSetDto>,
    val recordedBy: RecordedByDto,
    val recordedAt: String,
    val events: List<MatchEventDto>,
)

@Serializable
data class RecordedByDto(val userId: String, val displayName: String)

@Serializable
data class MatchEventDto(
    val userId: String,
    val displayName: String,
    val action: String,
    val createdAt: String,
)
