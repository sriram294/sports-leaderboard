package com.org.playboard.dto.device;

import jakarta.validation.constraints.NotBlank;

/**
 * Body for {@code DELETE /devices} — the Android client unregisters its FCM token
 * on sign-out so a signed-out device stops receiving pushes.
 */
public record UnregisterDeviceRequest(@NotBlank String token) {}
