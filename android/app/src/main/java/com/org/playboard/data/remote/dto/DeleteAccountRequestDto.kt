package com.org.playboard.data.remote.dto

import kotlinx.serialization.Serializable

/** Body required by `DELETE /api/v1/users/me`. */
@Serializable
data class DeleteAccountRequestDto(val confirmation: String)
