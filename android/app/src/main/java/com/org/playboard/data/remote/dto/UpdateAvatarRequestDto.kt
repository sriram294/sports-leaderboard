package com.org.playboard.data.remote.dto

import kotlinx.serialization.Serializable

/** Body of `PATCH /users/me/avatar`; mirrors the backend's {@code dto.user.UpdateAvatarRequest}. */
@Serializable
data class UpdateAvatarRequestDto(val avatarId: String)
