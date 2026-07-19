package com.org.playboard.data.remote.dto

import kotlinx.serialization.Serializable

/** `PATCH /groups/{id}/session` body — daily window "HH:mm", or both null to clear. */
@Serializable
data class UpdateSessionRequestDto(val start: String?, val end: String?)
