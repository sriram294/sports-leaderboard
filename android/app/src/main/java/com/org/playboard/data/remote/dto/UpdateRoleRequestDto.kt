package com.org.playboard.data.remote.dto

import kotlinx.serialization.Serializable

/** `PATCH /groups/{id}/members/{userId}` body — the new role ("admin" or "member"). */
@Serializable
data class UpdateRoleRequestDto(val role: String)
