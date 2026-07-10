package com.org.playboard.data.remote.dto

import kotlinx.serialization.Serializable

/** Mirrors the backend's {@code dto.auth.RefreshRequest}. */
@Serializable
data class RefreshRequestDto(val refreshToken: String)
