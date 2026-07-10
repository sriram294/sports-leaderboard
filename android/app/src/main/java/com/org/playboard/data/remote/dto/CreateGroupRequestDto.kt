package com.org.playboard.data.remote.dto

import kotlinx.serialization.Serializable

/** Body for `POST /groups` — see docs/backend/api-contracts.md § Groups. */
@Serializable
data class CreateGroupRequestDto(
    val name: String,
    val sportCode: String,
)
