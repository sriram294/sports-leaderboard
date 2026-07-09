package com.org.playboard.dto.match;

import java.time.Instant;
import java.util.UUID;

public record MatchEventDto(UUID userId, String displayName, String action, Instant createdAt) {}
