package com.org.playboard.dto.device;

import jakarta.validation.constraints.NotBlank;

/**
 * Body for {@code POST /devices} — the Android client registers its current FCM
 * token after sign-in (and on token rotation). {@code platform} defaults to
 * "android" when omitted.
 */
public record RegisterDeviceRequest(@NotBlank String token, String platform) {

    public String platformOrDefault() {
        return platform == null || platform.isBlank() ? "android" : platform.trim();
    }
}
