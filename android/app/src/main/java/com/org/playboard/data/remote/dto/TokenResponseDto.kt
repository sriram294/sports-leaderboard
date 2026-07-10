package com.org.playboard.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * Mirrors the backend's {@code dto.auth.TokenResponse}. [user] is present on
 * {@code POST /auth/google} and absent (null) on {@code POST /auth/refresh}.
 */
@Serializable
data class TokenResponseDto(
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Long,
    val user: UserSummaryDto? = null,
)
