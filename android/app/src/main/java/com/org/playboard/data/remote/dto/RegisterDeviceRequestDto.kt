package com.org.playboard.data.remote.dto

import kotlinx.serialization.Serializable

/** Mirrors the backend's {@code dto.device.RegisterDeviceRequest}. */
@Serializable
data class RegisterDeviceRequestDto(val token: String, val platform: String = "android")
