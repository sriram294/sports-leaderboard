package com.org.playboard.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * Response from `POST /groups/{groupId}/invites`. [expiresAt] is an ISO-8601
 * instant, or `null` when the invite never expires.
 */
@Serializable
data class InviteResponseDto(
    val code: String,
    val expiresAt: String? = null,
)
