package com.org.playboard.service.stats;

import java.time.Instant;
import java.util.UUID;

/** Published from match writes and handled only after their transaction commits. */
public record ProjectionRebuildRequested(UUID groupId, Instant periodStart, Instant periodEnd) {}
