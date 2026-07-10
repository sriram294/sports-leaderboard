package com.org.playboard.data.remote.dto

import kotlinx.serialization.Serializable

/** Mirrors the backend's {@code dto.auth.GoogleSignInRequest}. */
@Serializable
data class GoogleSignInRequestDto(val idToken: String)
