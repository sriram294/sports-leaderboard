package com.org.playboard.data.remote.dto

import kotlinx.serialization.Serializable

/** Body for `POST /groups/join` — see docs/backend/api-contracts.md § Groups. */
@Serializable
data class JoinGroupRequestDto(
    val code: String,
)
