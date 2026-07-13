package com.org.playboard.data.remote.dto

import kotlinx.serialization.Serializable

/** Mirrors the backend's {@code dto.device.UnregisterDeviceRequest}. */
@Serializable
data class UnregisterDeviceRequestDto(val token: String)
