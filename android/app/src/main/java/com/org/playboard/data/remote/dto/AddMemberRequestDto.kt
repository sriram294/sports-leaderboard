package com.org.playboard.data.remote.dto

import kotlinx.serialization.Serializable

/** Body for `POST /groups/{groupId}/members` — add a member by email + name. */
@Serializable
data class AddMemberRequestDto(val email: String, val displayName: String)
