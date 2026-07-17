package com.org.playboard.dto.match;

import java.util.UUID;

public record PlayerRefDto(UUID userId, String displayName, String avatarColor, String photoUrl, String avatarId) {}
