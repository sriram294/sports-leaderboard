package com.org.playboard.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class MembersResponseDto(
    val members: List<MemberDto>,
    /** Per-group guest fillers (role "guest"), returned separately from real players. */
    val guests: List<MemberDto> = emptyList(),
)
