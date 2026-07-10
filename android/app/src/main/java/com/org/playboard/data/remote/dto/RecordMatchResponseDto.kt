package com.org.playboard.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * `POST /groups/{groupId}/matches` returns the full MatchDetailDto; the Add
 * Match flow only needs to know it succeeded, so this captures just the id
 * (the app's Json ignores unknown keys).
 */
@Serializable
data class RecordMatchResponseDto(val id: String)
