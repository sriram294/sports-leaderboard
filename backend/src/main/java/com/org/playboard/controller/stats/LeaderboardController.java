package com.org.playboard.controller.stats;

import com.org.playboard.dto.stats.LeaderboardResponse;
import com.org.playboard.service.stats.StatsQueryService;
import com.org.playboard.service.stats.ArchivedLeaderboardService;
import com.org.playboard.repository.group.GroupRepository;
import com.org.playboard.service.group.GroupMembershipGuard;
import com.org.playboard.service.stats.LeaderboardSnapshotCache;
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
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import java.util.List;

@RestController
@RequestMapping("/api/v1/groups/{groupId}/leaderboard")
public class LeaderboardController {

    private final StatsQueryService statsQueryService;
    private final ArchivedLeaderboardService archivedLeaderboards;
    private final GroupRepository groups;
    private final GroupMembershipGuard membershipGuard;
    private final LeaderboardSnapshotCache snapshots;

    public LeaderboardController(StatsQueryService statsQueryService, ArchivedLeaderboardService archivedLeaderboards,
            GroupRepository groups, GroupMembershipGuard membershipGuard, LeaderboardSnapshotCache snapshots) {
        this.statsQueryService = statsQueryService;
        this.archivedLeaderboards = archivedLeaderboards;
        this.groups = groups;
        this.membershipGuard = membershipGuard;
        this.snapshots = snapshots;
    }

    /**
     * Ranking for the group. Supply both {@code from} and {@code to} (ISO-8601 instants)
     * for a windowed leaderboard — This Week / This Month, whose calendar boundaries the
     * client computes in device-local time — over the half-open interval {@code [from, to)}.
     * Omit both for the all-time ranking.
     */
    @GetMapping
    public ResponseEntity<LeaderboardResponse> getLeaderboard(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID groupId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) String month) {
        membershipGuard.requireActiveMember(groupId, userId);
        long version = groups.findById(groupId).map(com.org.playboard.entity.group.Group::getLeaderboardVersion).orElse(0L);
        String period = month != null ? month : from == null || to == null ? "all" : from + ":" + to;
        String algorithm = month != null ? "archived" : "live";
        LeaderboardResponse response = snapshots.get(groupId, period, algorithm, version).orElseGet(() -> {
            LeaderboardResponse loaded = month != null
                    ? archivedLeaderboards.standings(groupId, userId, YearMonth.parse(month))
                    : statsQueryService.getLeaderboard(groupId, userId, from, to);
            snapshots.put(groupId, period, algorithm, version, loaded);
            return loaded;
        });
        String etag = "\"" + groupId + ":" + period + ":" + response.algorithmVersion() + ":" + version + "\"";
        return ResponseEntity.ok().header(HttpHeaders.ETAG, etag).body(response);
    }

    @GetMapping("/months")
    public List<String> getArchivedMonths(@AuthenticationPrincipal UUID userId, @PathVariable UUID groupId) {
        return archivedLeaderboards.months(groupId, userId);
    }
}
