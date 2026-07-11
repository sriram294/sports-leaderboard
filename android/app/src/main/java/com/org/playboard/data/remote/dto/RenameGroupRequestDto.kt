package com.org.playboard.data.remote.dto

import kotlinx.serialization.Serializable

/** Body for `PATCH /groups/{groupId}` — rename a group (see docs/backend/api-contracts.md § Groups). */
@Serializable
data class RenameGroupRequestDto(
    val name: String,
)
