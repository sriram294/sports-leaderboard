package com.org.playboard.controller.stats;

import com.org.playboard.dto.stats.LeaderboardResponse;
import com.org.playboard.service.stats.StatsQueryService;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/groups/{groupId}/leaderboard")
public class LeaderboardController {

    private final StatsQueryService statsQueryService;

    public LeaderboardController(StatsQueryService statsQueryService) {
        this.statsQueryService = statsQueryService;
    }

    @GetMapping
    public LeaderboardResponse getLeaderboard(@AuthenticationPrincipal UUID userId, @PathVariable UUID groupId) {
        return statsQueryService.getLeaderboard(groupId, userId);
    }
}
