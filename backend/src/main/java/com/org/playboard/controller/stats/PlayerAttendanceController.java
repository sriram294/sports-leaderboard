package com.org.playboard.controller.stats;

import com.org.playboard.dto.stats.PlayerAttendanceDto;
import com.org.playboard.service.stats.StatsQueryService;
import java.time.Instant;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Backs the Profile attendance calendar — the days a player was in a match within a
 * {@code [from, to)} window. Same endpoint powers own attendance and, via the leaderboard
 * drill-down, another player's (the caller passes the {@code userId}). The window is
 * computed client-side in device-local time, so this stays timezone-agnostic.
 */
@RestController
@RequestMapping("/api/v1/groups/{groupId}/members/{userId}/attendance")
public class PlayerAttendanceController {

    private final StatsQueryService statsQueryService;

    public PlayerAttendanceController(StatsQueryService statsQueryService) {
        this.statsQueryService = statsQueryService;
    }

    @GetMapping
    public PlayerAttendanceDto getAttendance(
            @AuthenticationPrincipal UUID callerId,
            @PathVariable UUID groupId,
            @PathVariable UUID userId,
            @RequestParam Instant from,
            @RequestParam Instant to) {
        return statsQueryService.getAttendance(groupId, userId, callerId, from, to);
    }
}
