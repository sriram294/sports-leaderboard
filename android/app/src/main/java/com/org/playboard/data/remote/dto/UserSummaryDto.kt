package com.org.playboard.data.remote.dto

import kotlinx.serialization.Serializable

/** Mirrors the backend's {@code dto.user.UserSummaryDto}. */
@Serializable
data class UserSummaryDto(
    val id: String,
    val displayName: String,
    val email: String,
    val photoUrl: String?,
    val avatarColor: String,
)
