package com.org.playboard.dto.group;

import java.time.Instant;

public record InviteResponse(String code, Instant expiresAt) {}
