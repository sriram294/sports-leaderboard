package com.org.playboard.data.remote.dto

import kotlinx.serialization.Serializable

/** Body of `PATCH /users/me`; mirrors the backend's {@code dto.user.UpdateUserRequest}. */
@Serializable
data class UpdateUserRequestDto(val displayName: String)
