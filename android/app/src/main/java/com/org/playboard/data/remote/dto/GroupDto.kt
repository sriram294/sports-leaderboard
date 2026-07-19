package com.org.playboard.data.remote.dto

import kotlinx.serialization.Serializable

/** One group in the `GET /groups` response — see docs/backend/api-contracts.md § Groups. */
@Serializable
data class GroupDto(
    val id: String,
    val name: String,
    val avatarColor: String,
    val sportCode: String,
    val memberCount: Int,
    val matchCount: Int,
    val myRole: String,
    val sessionStart: String? = null,
    val sessionEnd: String? = null,
)
