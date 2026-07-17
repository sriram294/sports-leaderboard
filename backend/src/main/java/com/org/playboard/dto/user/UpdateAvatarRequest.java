package com.org.playboard.dto.user;

import jakarta.validation.constraints.NotBlank;

/** Selects one of the bundled default avatars (see {@code assets/avatars/}). */
public record UpdateAvatarRequest(@NotBlank String avatarId) {}
