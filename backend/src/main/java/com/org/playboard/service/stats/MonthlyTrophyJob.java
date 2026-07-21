package com.org.playboard.service.stats;

import com.org.playboard.dto.stats.LeaderboardEntryDto;
import com.org.playboard.entity.group.Group;
import com.org.playboard.entity.group.GroupRole;
import com.org.playboard.entity.group.MemberStatus;
import com.org.playboard.repository.group.GroupMemberRepository;
import com.org.playboard.repository.group.GroupRepository;
import com.org.playboard.repository.match.MatchRepository;
import com.org.playboard.repository.stats.MonthlyTrophyRepository;
import com.org.playboard.service.notification.NotificationCategory;
import com.org.playboard.service.notification.NotificationLogService;
import com.org.playboard.service.notification.PushNotificationService;
import com.org.playboard.service.stats.LeaderboardRanker.Standings;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Awards the monthly leaderboard crown: the top ranked player of each completed calendar
 * month, recorded permanently.
 *
 * <p><b>Why this scans rather than firing at the month boundary.</b> The obvious shape — a
 * cron at 00:00 on the 1st — cannot work here. Railway runs this service with app-sleeping,
 * so {@code @Scheduled} only ticks while something is keeping it awake, and the keepalive
 * covers 05:30–11:15 IST. A midnight trigger is therefore *guaranteed* to fire into a
 * sleeping container and be missed, with nothing to retry it.
 *
 * <p>So the job instead asks "is there a completed month with no verdict yet?" That is
 * idempotent, self-healing across downtime (it catches up whenever the service next wakes),
 * and needs no separate backfill path for history that predates the feature.
 *
 * <p>Repeat runs are safe: claiming the month is a single {@code insert ... on conflict do
 * nothing}, so re-scanning is wasted work rather than a double award, including across a
 * redeploy or two concurrent Railway instances.
 */
@Component
public class MonthlyTrophyJob {

    private static final Logger log = LoggerFactory.getLogger(MonthlyTrophyJob.class);

    private final MatchRepository matchRepository;
    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final MonthlyTrophyRepository trophyRepository;
    private final StatsQueryService statsQueryService;
    private final NotificationLogService notificationLog;
    private final PushNotificationService pushNotificationService;
    private final ZoneId zone;

    public MonthlyTrophyJob(
            MatchRepository matchRepository,
            GroupRepository groupRepository,
            GroupMemberRepository groupMemberRepository,
            MonthlyTrophyRepository trophyRepository,
            StatsQueryService statsQueryService,
            NotificationLogService notificationLog,
            PushNotificationService pushNotificationService,
            // Nothing in the schema stores a per-group timezone, so the job owns the
            // definition of "a month". Asia/Kolkata matches where the group actually plays,
            // and India has no DST, so the offset is stable year-round.
            @Value("${playboard.trophies.zone:Asia/Kolkata}") ZoneId zone) {
        this.matchRepository = matchRepository;
        this.groupRepository = groupRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.trophyRepository = trophyRepository;
        this.statsQueryService = statsQueryService;
        this.notificationLog = notificationLog;
        this.pushNotificationService = pushNotificationService;
        this.zone = zone;
    }

    // Same reasoning as SessionRankChangeJob: an initial delay keeps this off the startup
    // path and out of every short-lived @SpringBootTest boot. The interval is long because
    // the work only changes once a month — it is a catch-up sweep, not a poll.
    @Scheduled(
            initialDelayString = "${playboard.trophies.scan-initial-delay:PT3M}",
            fixedDelayString = "${playboard.trophies.scan-interval:PT6H}")
    public void awardCompletedMonths() {
        Instant now = Instant.now();
        for (MatchRepository.GroupFirstMatch group : matchRepository.findGroupFirstMatches()) {
            try {
                processGroup(group.getGroupId(), group.getFirstPlayedAt(), now);
            } catch (Exception e) {
                // One unhealthy group must not stop the others being awarded.
                log.warn("Monthly trophy scan failed for group {}.", group.getGroupId(), e);
            }
        }
    }

    void processGroup(UUID groupId, Instant firstPlayedAt, Instant now) {
        Set<YearMonth> decided = trophyRepository.findDecidedMonths(groupId).stream()
                .map(YearMonth::from)
                .collect(Collectors.toSet());

        for (YearMonth month : completedMonths(firstPlayedAt, now, zone)) {
            if (decided.contains(month)) {
                continue;
            }
            awardMonth(groupId, month);
        }
    }

    private void awardMonth(UUID groupId, YearMonth month) {
        Instant from = month.atDay(1).atStartOfDay(zone).toInstant();
        Instant to = month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant();

        Standings standings = statsQueryService.rankedStandings(groupId, from, to, null);
        Optional<LeaderboardEntryDto> winner = pickWinner(standings);

        // A month with no qualifying player still gets a row, with a null user. Without that
        // verdict the month stays "undecided" and is re-evaluated on every run, forever.
        // See pickWinner for when that actually happens — it is rarer than it looks.
        int claimed = trophyRepository.awardIfAbsent(
                groupId,
                winner.map(LeaderboardEntryDto::userId).orElse(null),
                month.atDay(1),
                winner.map(LeaderboardEntryDto::rating).orElse(null),
                winner.map(LeaderboardEntryDto::gamesPlayed).orElse(null),
                winner.map(LeaderboardEntryDto::wins).orElse(null));

        if (claimed == 0) {
            // Another instance decided this month first; it owns the announcement.
            return;
        }
        if (winner.isEmpty()) {
            log.info("No eligible winner for group {} in {}; month recorded as undecided.", groupId, month);
            return;
        }
        log.info("Group {} crowned {} for {}.", groupId, winner.get().userId(), month);
        announce(groupId, month, winner.get());
    }

    /**
     * The month's winner, or empty when nobody qualifies.
     *
     * <p>Only <em>ranked</em> players can win: a provisional player has played too few games
     * for the confidence-adjusted rating to mean anything, and crowning one would hand the
     * month to whoever turned up twice and got lucky. Standings put every ranked player ahead
     * of every provisional one, so the first entry wins unless it is itself provisional.
     *
     * <p>In practice the provisional guard rarely fires, because the threshold slides with the
     * group's median games — the median player always clears it. The case that actually
     * produces an empty month is empty standings: nobody <em>eligible</em> played it, because
     * every participant has since left the group or only guests were on court.
     */
    static Optional<LeaderboardEntryDto> pickWinner(Standings standings) {
        return standings.entries().stream().findFirst().filter(entry -> !entry.provisional());
    }

    /**
     * Every calendar month from the group's first match up to the last <em>completed</em>
     * one, oldest first. The current month is excluded — it is still being played.
     */
    static List<YearMonth> completedMonths(Instant firstPlayedAt, Instant now, ZoneId zone) {
        YearMonth first = YearMonth.from(LocalDate.ofInstant(firstPlayedAt, zone));
        YearMonth current = YearMonth.from(LocalDate.ofInstant(now, zone));

        List<YearMonth> months = new ArrayList<>();
        for (YearMonth m = first; m.isBefore(current); m = m.plusMonths(1)) {
            months.add(m);
        }
        return months;
    }

    private void announce(UUID groupId, YearMonth month, LeaderboardEntryDto winner) {
        Group group = groupRepository.findById(groupId).orElse(null);
        if (group == null) {
            return;
        }
        String monthName = month.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH);
        String body = winner.displayName() + " topped " + group.getName() + " in " + monthName + ".";
        String dedupeKey = "monthly_trophy:" + groupId + ":" + month;

        // The trophy row above is the real double-send guard — only the instance that claimed
        // the month gets here. The per-recipient claim is kept for the audit trail the
        // notification log exists to provide ("why did I get this?").
        List<UUID> recipients = new ArrayList<>();
        for (UUID userId : activeMemberIds(groupId)) {
            if (notificationLog.claim(userId, NotificationCategory.GROUP_UPDATE, dedupeKey)) {
                recipients.add(userId);
            }
        }
        pushNotificationService.sendToUsers(
                recipients,
                NotificationCategory.GROUP_UPDATE,
                group.getName(),
                body,
                Map.of("type", "monthly_trophy", "groupId", groupId.toString()));
    }

    /** Active, non-guest members — guests have no account and never receive pushes. */
    private List<UUID> activeMemberIds(UUID groupId) {
        return groupMemberRepository.findByGroupIdAndStatus(groupId, MemberStatus.ACTIVE).stream()
                .filter(member -> member.getRole() != GroupRole.GUEST)
                .map(member -> member.getUser().getId())
                .toList();
    }
}
