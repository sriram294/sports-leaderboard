package com.org.playboard.data.remote.dto

import kotlinx.serialization.Serializable

/** Envelope of `GET /groups`. */
@Serializable
data class GroupsResponseDto(
    val groups: List<GroupDto>,
)
