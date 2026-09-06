package com.org.playboard.controller.stats;

import com.org.playboard.dto.stats.PartnerDto;
import com.org.playboard.service.stats.StatsQueryService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Partner counts, fetched separately from {@link PlayerStatsController} so a client can
 * defer these queries until the player actually expands a "Partners" list rather than
 * loading them eagerly with the rest of the page.
 */
@RestController
public class PartnerStatsController {

    private final StatsQueryService statsQueryService;

    public PartnerStatsController(StatsQueryService statsQueryService) {
        this.statsQueryService = statsQueryService;
    }

    /** Partners within the optional [from, to) window, most games together first. */
    @GetMapping("/api/v1/groups/{groupId}/members/{userId}/stats/partners")
    public List<PartnerDto> getPartners(
            @AuthenticationPrincipal UUID callerId,
            @PathVariable UUID groupId,
            @PathVariable UUID userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return statsQueryService.getPartners(groupId, userId, callerId, from, to);
    }
}
