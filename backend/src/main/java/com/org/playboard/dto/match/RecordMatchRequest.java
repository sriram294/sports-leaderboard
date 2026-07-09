package com.org.playboard.dto.match;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Same shape for both {@code POST} (create) and {@code PATCH} (full replace). */
public record RecordMatchRequest(
        @NotNull Instant playedAt,
        @NotEmpty @Valid List<TeamInput> teams,
        @NotEmpty @Valid List<SetInput> sets,
        short winningTeamNo) {

    public record TeamInput(short teamNo, @NotEmpty List<UUID> playerIds) {}

    public record SetInput(short setNo, short team1Score, short team2Score) {}
}
