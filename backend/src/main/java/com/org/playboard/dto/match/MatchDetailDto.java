package com.org.playboard.dto.match;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record MatchDetailDto(
        UUID id,
        Instant playedAt,
        List<TeamDto> teams,
        List<SetDto> sets,
        RecordedByDto recordedBy,
        Instant recordedAt,
        List<MatchEventDto> events) {}
