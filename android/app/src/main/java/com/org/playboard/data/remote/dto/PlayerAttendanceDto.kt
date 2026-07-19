package com.org.playboard.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * `GET /groups/{id}/members/{userId}/attendance` — the distinct match instants (ISO-8601)
 * a player was in within the queried window. The client buckets these into local calendar
 * days to paint the Profile attendance grid.
 */
@Serializable
data class PlayerAttendanceDto(
    val playedAt: List<String> = emptyList(),
)
