package com.org.playboard.controller.stats;

import com.org.playboard.dto.stats.MonthlyTrophyDto;
import com.org.playboard.service.stats.MonthlyTrophyService;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/groups/{groupId}/trophies")
public class MonthlyTrophyController {

    /** Matches the Stats screen's six-month strip; also caps an unbounded history read. */
    private static final int DEFAULT_LIMIT = 6;

    private static final int MAX_LIMIT = 60;

    private final MonthlyTrophyService monthlyTrophyService;

    public MonthlyTrophyController(MonthlyTrophyService monthlyTrophyService) {
        this.monthlyTrophyService = monthlyTrophyService;
    }

    /**
     * The group's monthly winners, newest first — its roll of honour.
     *
     * <p>Months that closed with no qualifying player are omitted entirely; they exist in the
     * table only so the award job knows not to reconsider them.
     */
    @GetMapping
    public List<MonthlyTrophyDto> getTrophies(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID groupId,
            @RequestParam(defaultValue = "" + DEFAULT_LIMIT) int limit) {
        return monthlyTrophyService.forGroup(groupId, userId, Math.clamp(limit, 1, MAX_LIMIT));
    }
}
