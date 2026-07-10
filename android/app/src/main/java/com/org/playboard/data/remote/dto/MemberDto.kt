package com.org.playboard.data.remote.dto

import kotlinx.serialization.Serializable

/** One member in the `GET /groups/{groupId}/members` roster. */
@Serializable
data class MemberDto(
    val userId: String,
    val displayName: String,
    val photoUrl: String? = null,
    val avatarColor: String,
    val role: String,
)
