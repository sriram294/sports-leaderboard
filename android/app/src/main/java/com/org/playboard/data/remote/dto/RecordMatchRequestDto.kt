package com.org.playboard.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * Body for `POST /groups/{groupId}/matches` (see docs/backend/api-contracts.md).
 * [playedAt] is an ISO-8601 instant; [winningTeamNo] is 1 or 2.
 */
@Serializable
data class RecordMatchRequestDto(
    val playedAt: String,
    val teams: List<TeamInputDto>,
    val sets: List<SetInputDto>,
    val winningTeamNo: Int,
)

@Serializable
data class TeamInputDto(val teamNo: Int, val playerIds: List<String>)

@Serializable
data class SetInputDto(val setNo: Int, val team1Score: Int, val team2Score: Int)
