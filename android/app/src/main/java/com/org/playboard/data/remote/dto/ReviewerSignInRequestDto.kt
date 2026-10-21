package com.org.playboard.data.remote.dto

import kotlinx.serialization.Serializable

/** Mirrors the backend's {@code dto.auth.ReviewerSignInRequest} (hidden store-review login). */
@Serializable
data class ReviewerSignInRequestDto(val username: String, val password: String) {
    // Keep the password out of logs / crash reports that stringify the request.
    override fun toString(): String = "ReviewerSignInRequestDto(username=$username, password=***)"
}
