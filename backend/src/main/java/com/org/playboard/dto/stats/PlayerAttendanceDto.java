package com.org.playboard.dto.stats;

import java.time.Instant;
import java.util.List;

/**
 * Backs the Profile attendance calendar: the distinct match instants a player was in
 * within the queried window. Instants serialize to ISO-8601 strings; the client buckets
 * them into local calendar days to paint the month grid.
 */
public record PlayerAttendanceDto(List<Instant> playedAt) {}
