package com.org.playboard.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class MembersResponseDto(val members: List<MemberDto>)
