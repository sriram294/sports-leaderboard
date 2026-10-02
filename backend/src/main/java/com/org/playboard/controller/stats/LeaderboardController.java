package com.org.playboard.controller.stats;

import com.org.playboard.dto.stats.LeaderboardResponse;
import com.org.playboard.service.stats.StatsQueryService;
import com.org.playboard.service.stats.ArchivedLeaderboardService;
import java.time.Instant;
import java.time.YearMonth;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
@RequestMapping("/api/v1/groups/{groupId}/leaderboard")
public class LeaderboardController {

    private final StatsQueryService statsQueryService;
    private final ArchivedLeaderboardService archivedLeaderboards;

    public LeaderboardController(StatsQueryService statsQueryService, ArchivedLeaderboardService archivedLeaderboards) {
        this.statsQueryService = statsQueryService;
        this.archivedLeaderboards = archivedLeaderboards;
    }

    /**
     * Ranking for the group. Supply both {@code from} and {@code to} (ISO-8601 instants)
     * for a windowed leaderboard — This Week / This Month, whose calendar boundaries the
     * client computes in device-local time — over the half-open interval {@code [from, to)}.
     * Omit both for the all-time ranking.
     */
    @GetMapping
    public LeaderboardResponse getLeaderboard(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID groupId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) String month) {
        if (month != null) return archivedLeaderboards.standings(groupId, userId, YearMonth.parse(month));
        return statsQueryService.getLeaderboard(groupId, userId, from, to);
    }

    @GetMapping("/months")
    public List<String> getArchivedMonths(@AuthenticationPrincipal UUID userId, @PathVariable UUID groupId) {
        return archivedLeaderboards.months(groupId, userId);
    }
}
