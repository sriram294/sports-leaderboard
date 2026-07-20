package com.org.playboard.service.notification;

import com.org.playboard.dto.stats.LeaderboardEntryDto;
import com.org.playboard.entity.group.Group;
import com.org.playboard.repository.group.GroupRepository;
import com.org.playboard.repository.match.MatchRepository;
import com.org.playboard.service.stats.StatsQueryService;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Tells players how their leaderboard position moved once a group finishes playing.
 *
 * <p>There is no "session ended" signal in the data model — {@code groups.session_end}
 * is static wall-clock config with no timezone, never joined against matches — so a
 * session is inferred from <em>inactivity</em>: it is over once no match has been logged
 * for {@code quietPeriod}. That needs no timezone, adapts to nights that run long or
 * finish early, and works for groups that never configured a window.
 *
 * <p>The scan re-examines a group on every tick until it falls out of the lookback
 * window. That is deliberate: repeat sends are prevented by claiming a
 * {@code notification_log} row per recipient, so re-running is merely wasted work, never
 * a duplicate push — including across a redeploy or a second Railway instance.
 */
@Component
public class SessionRankChangeJob {

    private static final Logger log = LoggerFactory.getLogger(SessionRankChangeJob.class);

    private final MatchRepository matchRepository;
    private final GroupRepository groupRepository;
    private final StatsQueryService statsQueryService;
    private final NotificationLogService notificationLog;
    private final PushNotificationService pushNotificationService;
    private final Duration quietPeriod;
    private final Duration lookback;

    public SessionRankChangeJob(
            MatchRepository matchRepository,
            GroupRepository groupRepository,
            StatsQueryService statsQueryService,
            NotificationLogService notificationLog,
            PushNotificationService pushNotificationService,
            @Value("${playboard.notifications.session-quiet-period:PT90M}") Duration quietPeriod,
            @Value("${playboard.notifications.session-lookback:PT24H}") Duration lookback) {
        this.matchRepository = matchRepository;
        this.groupRepository = groupRepository;
        this.statsQueryService = statsQueryService;
        this.notificationLog = notificationLog;
        this.pushNotificationService = pushNotificationService;
        this.quietPeriod = quietPeriod;
        this.lookback = lookback;
    }

    // fixedDelay would otherwise fire the moment the context is up — during startup, and
    // on every short-lived @SpringBootTest boot. The initial delay lets the app settle
    // first; nothing is lost, since an ended session stays detectable for the whole
    // lookback window.
    @Scheduled(
            initialDelayString = "${playboard.notifications.session-scan-initial-delay:PT2M}",
            fixedDelayString = "${playboard.notifications.session-scan-interval:PT15M}")
    public void scanForEndedSessions() {
        Instant now = Instant.now();
        List<UUID> groupIds = matchRepository.findGroupIdsWithMatchesSince(now.minus(lookback));
        for (UUID groupId : groupIds) {
            try {
                processGroup(groupId, now);
            } catch (Exception e) {
                // One unhealthy group must not stop the others being processed.
                log.warn("Rank-change scan failed for group {}.", groupId, e);
            }
        }
    }

    void processGroup(UUID groupId, Instant now) {
        List<Instant> playTimes = matchRepository.findPlayedAtSince(groupId, now.minus(lookback));
        Optional<Instant> sessionStart = endedSessionStart(playTimes, now, quietPeriod);
        if (sessionStart.isEmpty()) {
            return;
        }
        Instant start = sessionStart.get();

        // Both sides through the same routine, so the two rankings are directly comparable.
        // `to` is exclusive, so the session's own first match is correctly outside "before".
        List<LeaderboardEntryDto> before = statsQueryService.rankedStandings(groupId, Instant.EPOCH, start);
        List<LeaderboardEntryDto> after = statsQueryService.rankedStandings(groupId, Instant.EPOCH, now);
        List<RankChange> changes = RankChange.between(before, after);
        if (changes.isEmpty()) {
            return;
        }

        Group group = groupRepository.findById(groupId).orElse(null);
        if (group == null) {
            return;
        }
        String groupName = group.getName();
        String dedupeKey = "rank_change:" + groupId + ":" + start;

        int sent = 0;
        for (RankChange change : changes) {
            // Claim before sending: the unique index, not this read, is what makes a
            // repeat impossible when two instances scan at once.
            if (!notificationLog.claim(change.userId(), NotificationCategory.DAILY_SUMMARY, dedupeKey)) {
                continue;
            }
            pushNotificationService.sendToUsers(
                    List.of(change.userId()),
                    NotificationCategory.DAILY_SUMMARY,
                    groupName,
                    describe(change, groupName),
                    Map.of("type", "rank_change", "groupId", groupId.toString()));
            sent++;
        }
        if (sent > 0) {
            log.info("Session ended in group {} (started {}): notified {} player(s).", groupId, start, sent);
        }
    }

    /**
     * Start of the most recent <em>finished</em> session, or empty if the group is still
     * playing (or hasn't). Walks back from the latest match while consecutive matches sit
     * closer together than {@code quietPeriod}; the first larger gap is the boundary
     * between this session and the previous one.
     *
     * @param ascendingPlayTimes play times in ascending order
     */
    static Optional<Instant> endedSessionStart(
            List<Instant> ascendingPlayTimes, Instant now, Duration quietPeriod) {
        if (ascendingPlayTimes.isEmpty()) {
            return Optional.empty();
        }
        Instant latest = ascendingPlayTimes.get(ascendingPlayTimes.size() - 1);
        if (Duration.between(latest, now).compareTo(quietPeriod) < 0) {
            return Optional.empty(); // still playing
        }
        int i = ascendingPlayTimes.size() - 1;
        while (i > 0
                && Duration.between(ascendingPlayTimes.get(i - 1), ascendingPlayTimes.get(i))
                                .compareTo(quietPeriod)
                        < 0) {
            i--;
        }
        return Optional.of(ascendingPlayTimes.get(i));
    }

    /** Rank 1 is top, so a climb means the number went down. */
    static String describe(RankChange change, String groupName) {
        if (change.isFirstTimeRanked()) {
            return "You're on the board at #" + change.currentRank() + " in " + groupName + ".";
        }
        int gained = change.placesGained();
        if (gained > 0) {
            return "You moved up " + places(gained) + " — you're #" + change.currentRank() + " in " + groupName + ".";
        }
        return "You slipped " + places(-gained) + " to #" + change.currentRank() + " in " + groupName + ".";
    }

    private static String places(int count) {
        return count == 1 ? "1 place" : count + " places";
    }
}
