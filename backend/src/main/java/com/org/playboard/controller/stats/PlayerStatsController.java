package com.org.playboard.controller.stats;

import com.org.playboard.dto.stats.PlayerStatsDto;
import com.org.playboard.service.stats.StatsQueryService;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Backs both the Profile tab (own stats) and tapping a player from the leaderboard — same endpoint, different {@code userId}. */
@RestController
@RequestMapping("/api/v1/groups/{groupId}/members/{userId}/stats")
public class PlayerStatsController {

    private final StatsQueryService statsQueryService;

    public PlayerStatsController(StatsQueryService statsQueryService) {
        this.statsQueryService = statsQueryService;
    }

    @GetMapping
    public PlayerStatsDto getPlayerStats(
            @AuthenticationPrincipal UUID callerId, @PathVariable UUID groupId, @PathVariable UUID userId) {
        return statsQueryService.getPlayerStats(groupId, userId, callerId);
    }
}
