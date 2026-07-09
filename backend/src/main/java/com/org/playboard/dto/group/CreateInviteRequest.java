package com.org.playboard.dto.group;

import jakarta.validation.constraints.Positive;

/** Both fields are optional — {@code null} means unlimited uses / no expiry. */
public record CreateInviteRequest(@Positive Integer maxUses, @Positive Integer expiresInHours) {}
