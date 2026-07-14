package com.org.playboard.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class AppUpdateDto(
    val versionCode: Int? = null,
    val versionName: String? = null,
    val downloadUrl: String? = null,
    val available: Boolean = false,
)
