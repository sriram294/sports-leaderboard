package com.org.playboard.service.stats;

import com.org.playboard.common.ApiException;
import com.org.playboard.dto.match.MatchSummaryDto;
import com.org.playboard.dto.stats.LeaderboardEntryDto;
import com.org.playboard.dto.stats.LeaderboardResponse;
import com.org.playboard.dto.stats.MonthlyFinishDto;
import com.org.playboard.dto.stats.PartnerDto;
import com.org.playboard.dto.stats.PlayerAttendanceDto;
import com.org.playboard.dto.stats.PlayerStatsDto;
import com.org.playboard.entity.group.GroupMember;
import com.org.playboard.entity.group.GroupRole;
import com.org.playboard.entity.group.MemberStatus;
import com.org.playboard.entity.stats.MemberStats;
import com.org.playboard.entity.user.User;
import com.org.playboard.repository.group.GroupMemberRepository;
import com.org.playboard.repository.match.MatchParticipantRepository;
import com.org.playboard.repository.match.MatchParticipantRepository.PartnerRow;
import com.org.playboard.repository.match.MatchParticipantRepository.RecentFormRow;
import com.org.playboard.repository.match.MatchParticipantRepository.WindowedStatRow;
import com.org.playboard.repository.stats.MemberStatsRepository;
import com.org.playboard.repository.stats.MonthlyStandingRepository;
import com.org.playboard.service.group.GroupMembershipGuard;
import com.org.playboard.service.match.MatchService;
import com.org.playboard.service.stats.LeaderboardRanker.RawStatRow;
import com.org.playboard.service.stats.LeaderboardRanker.Standings;
import com.org.playboard.service.user.AvatarUrlResolver;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StatsQueryService {

    private static final int RECENT_MATCHES_LIMIT = 5;
    private static final int RECENT_FORM_LIMIT = 10;

    private final GroupMembershipGuard membershipGuard;
    private final GroupMemberRepository groupMemberRepository;
    private final MemberStatsRepository memberStatsRepository;
    private final MatchParticipantRepository matchParticipantRepository;
    private final MatchService matchService;
    private final AvatarUrlResolver avatarUrls;
    private final MonthlyTrophyService monthlyTrophyService;
    private final MonthlyStandingRepository monthlyStandingRepository;
    private final TeamRatingService teamRatingService;
    private final boolean teamV1Enabled;
    private static final Instant TEAM_V1_BOUNDARY = Instant.parse("2026-08-31T18:30:00Z");

    public StatsQueryService(
            GroupMembershipGuard membershipGuard,
            GroupMemberRepository groupMemberRepository,
            MemberStatsRepository memberStatsRepository,
            MatchParticipantRepository matchParticipantRepository,
            MatchService matchService,
            AvatarUrlResolver avatarUrls,
            MonthlyTrophyService monthlyTrophyService,
            MonthlyStandingRepository monthlyStandingRepository,
            TeamRatingService teamRatingService,
            @Value("${playboard.ratings.team-v1-enabled:true}") boolean teamV1Enabled) {
        this.membershipGuard = membershipGuard;
        this.groupMemberRepository = groupMemberRepository;
        this.memberStatsRepository = memberStatsRepository;
        this.matchParticipantRepository = matchParticipantRepository;
        this.matchService = matchService;
        this.avatarUrls = avatarUrls;
        this.monthlyTrophyService = monthlyTrophyService;
        this.monthlyStandingRepository = monthlyStandingRepository;
        this.teamRatingService = teamRatingService;
        this.teamV1Enabled = teamV1Enabled;
    }

    @Transactional(readOnly = true)
    public LeaderboardResponse getLeaderboard(UUID groupId, UUID callerId) {
        return getLeaderboard(groupId, callerId, null, null);
    }

    /**
     * Leaderboard for a group. When {@code from} and {@code to} are both supplied the
     * ranking is computed for that {@code [from, to)} window by aggregating raw matches
     * (This Month); otherwise it reads the all-time {@code member_stats} snapshot.
     *
     * <p>Both paths collect raw totals and then hand them to {@link LeaderboardRanker}, so
     * ordering, rating and the provisional threshold have a single implementation. They
     * previously ordered independently — one in JPQL, one in a Java comparator — which
     * could report a rank change that never happened.
     */
    @Transactional(readOnly = true)
    public LeaderboardResponse getLeaderboard(UUID groupId, UUID callerId, Instant from, Instant to) {
        membershipGuard.requireActiveMember(groupId, callerId);
        boolean teamV1 = teamV1Enabled && from != null && to != null && !from.isBefore(TEAM_V1_BOUNDARY);
        Standings standings = from == null || to == null
                ? allTimeStandings(groupId, null)
                : teamV1 ? teamRankedStandings(groupId, from, to) : rankedStandings(groupId, from, to, null);
        return new LeaderboardResponse(standings.entries(), standings.minGamesToRank(),
                teamV1 ? TeamRatingService.ALGORITHM_VERSION : "wilson-v1",
                teamV1 ? TeamRatingService.PERIOD : (from == null ? "all-time" : "window"));
    }

    /**
     * All-time standings from the {@code member_stats} snapshot.
     *
     * @param thresholdOverride see {@link #rankedStandings}.
     */
    @Transactional(readOnly = true)
    public Standings allTimeStandings(UUID groupId, Integer thresholdOverride) {
        // Only active, non-guest members rank — a removed member's member_stats row lingers
        // otherwise, and guests never appear. Mirrors rankedStandings' eligibility.
        Set<UUID> eligible = groupMemberRepository.findByGroupIdAndStatus(groupId, MemberStatus.ACTIVE).stream()
                .filter(member -> member.getRole() != GroupRole.GUEST)
                .map(member -> member.getUser().getId())
                .collect(Collectors.toSet());

        Map<UUID, User> users = new HashMap<>();
        List<RawStatRow> rows = new ArrayList<>();
        for (MemberStats stats : memberStatsRepository.findByGroupId(groupId)) {
            UUID userId = stats.getId().getUserId();
            if (stats.getMatchesPlayed() == 0 || !eligible.contains(userId)) {
                continue;
            }
            users.put(userId, stats.getUser());
            rows.add(new RawStatRow(
                    userId,
                    stats.getMatchesPlayed(),
                    stats.getWins(),
                    stats.getPointsFor(),
                    stats.getPointsAgainst(),
                    stats.getCurrentStreak(),
                    stats.getBestStreak()));
        }
        Map<UUID, List<Boolean>> form = recentFormByUser(groupId, null, null);
        return LeaderboardRanker.rank(rows, thresholdOverride, entryFactory(users, form));
    }

    /**
     * Ranked standings computed from raw matches in {@code [from, to)}. Backs both the
     * This Month board and the end-of-session rank-change notification, which calls it
     * twice — with {@code from = EPOCH} so the window becomes "everything up to
     * {@code to}" — to compare standings before and after a session.
     *
     * @param thresholdOverride pins the provisional threshold instead of deriving it from
     *     this window's median. Callers comparing two standings <b>must</b> pin the second
     *     to the first's {@link Standings#minGamesToRank()}: the threshold is a group
     *     statistic, so one player's games can shift it, reshuffle the ranked set, and make
     *     it look like players who never played changed rank.
     *
     * <p>Transactional in its own right because the job calls it outside a request.
     */
    @Transactional(readOnly = true)
    public Standings rankedStandings(UUID groupId, Instant from, Instant to, Integer thresholdOverride) {
        // Scheduled monthly snapshots use this method directly rather than the HTTP
        // envelope. Keep the same September algorithm and metadata on those immutable
        // records as on the live current-period endpoint.
        if (teamV1Enabled && thresholdOverride == null && from != null && to != null && !from.isBefore(TEAM_V1_BOUNDARY)) {
            return teamRankedStandings(groupId, from, to);
        }
        // Only active, non-guest members can rank (guests are excluded from the
        // leaderboard, matching the all-time member_stats path).
        Map<UUID, User> eligible = new HashMap<>();
        for (GroupMember member : groupMemberRepository.findByGroupIdAndStatus(groupId, MemberStatus.ACTIVE)) {
            if (member.getRole() != GroupRole.GUEST) {
                eligible.put(member.getUser().getId(), member.getUser());
            }
        }

        Map<UUID, int[]> streaks = windowedStreaks(groupId, from, to);
        List<RawStatRow> rows = new ArrayList<>();
        for (WindowedStatRow row : matchParticipantRepository.aggregateWindowedStats(groupId, from, to)) {
            int gamesPlayed = (int) row.getGamesPlayed();
            if (!eligible.containsKey(row.getUserId()) || gamesPlayed == 0) {
                continue;
            }
            int[] playerStreaks = streaks.getOrDefault(row.getUserId(), new int[] {0, 0});
            rows.add(new RawStatRow(
                    row.getUserId(),
                    gamesPlayed,
                    (int) row.getWins(),
                    (int) row.getPointsFor(),
                    (int) row.getPointsAgainst(),
                    playerStreaks[0],
                    playerStreaks[1]));
        }
        Map<UUID, List<Boolean>> form = recentFormByUser(groupId, from, to);
        return LeaderboardRanker.rank(rows, thresholdOverride, entryFactory(eligible, form));
    }

    private Standings teamRankedStandings(UUID groupId, Instant from, Instant to) {
        Map<UUID, User> eligible = new HashMap<>();
        for (GroupMember member : groupMemberRepository.findByGroupIdAndStatus(groupId, MemberStatus.ACTIVE)) {
            if (member.getRole() != GroupRole.GUEST) eligible.put(member.getUser().getId(), member.getUser());
        }
        Map<UUID, TeamRatingService.PlayerRating> ratings = teamRatingService.replay(groupId, from, to, eligible);
        Map<UUID, int[]> streaks = windowedStreaks(groupId, from, to);
        Map<UUID, List<Boolean>> form = recentFormByUser(groupId, from, to);
        List<LeaderboardEntryDto> qualified = new ArrayList<>();
        List<LeaderboardEntryDto> provisional = new ArrayList<>();
        for (WindowedStatRow row : matchParticipantRepository.aggregateWindowedStats(groupId, from, to)) {
            TeamRatingService.PlayerRating team = ratings.get(row.getUserId());
            if (team == null || team.games() == 0) continue;
            int[] streak = streaks.getOrDefault(row.getUserId(), new int[] {0, 0});
            BigDecimal rating = team.displayRating();
            LeaderboardEntryDto entry = new LeaderboardEntryDto(0, row.getUserId(), eligible.get(row.getUserId()).getDisplayName(),
                    avatarUrls.resolve(eligible.get(row.getUserId()).getPhotoUrl()), eligible.get(row.getUserId()).getAvatarId(),
                    eligible.get(row.getUserId()).getAvatarColor(), team.games(), team.wins(),
                    team.games() - team.wins(), (int) row.getPointsFor(), (int) row.getPointsAgainst(),
                    LeaderboardRanker.winRate(team.wins(), team.games()), streak[0], streak[1], rating,
                    !team.qualified(), form.getOrDefault(row.getUserId(), List.of()), TeamRatingService.ALGORITHM_VERSION,
                    TeamRatingService.PERIOD, team.uniquePartners(), team.maxPartnerShare(), team.provisionalReason());
            (team.qualified() ? qualified : provisional).add(entry);
        }
        Comparator<LeaderboardEntryDto> order = (a, b) -> {
            TeamRatingService.PlayerRating ar = ratings.get(a.userId());
            TeamRatingService.PlayerRating br = ratings.get(b.userId());
            double delta = TeamRatingCalculator.conservativeScore(new TeamRatingCalculator.Rating(ar.mean(), ar.sigma()))
                    - TeamRatingCalculator.conservativeScore(new TeamRatingCalculator.Rating(br.mean(), br.sigma()));
            if (Math.abs(delta) > .25) return delta > 0 ? -1 : 1;
            // Keep the user-visible number aligned with the row order. The internal
            // conservative score still decides clearly separated players; inside its
            // tie band, comparing the same display mapping prevents confusing cases
            // such as #2 showing 72.1 above #3 showing 72.5.
            int byDisplayRating = br.displayRating().compareTo(ar.displayRating());
            if (byDisplayRating != 0) return byDisplayRating;
            int byDiff = Integer.compare(b.pointsDiff(), a.pointsDiff());
            if (byDiff != 0) return byDiff;
            int byWins = Integer.compare(b.wins(), a.wins());
            return byWins != 0 ? byWins : a.userId().compareTo(b.userId());
        };
        qualified.sort(order); provisional.sort(order);
        List<LeaderboardEntryDto> result = new ArrayList<>();
        int rank = 1;
        for (LeaderboardEntryDto entry : qualified) result.add(entry.withRank(rank++));
        for (LeaderboardEntryDto entry : provisional) result.add(entry.withRank(rank++));
        return new Standings(result, LeaderboardRanker.minGamesToRank(ratings.values().stream().map(TeamRatingService.PlayerRating::games).toList()));
    }

    private Map<UUID, int[]> windowedStreaks(UUID groupId, Instant from, Instant to) {
        Map<UUID, List<Boolean>> results = new HashMap<>();
        for (var row : matchParticipantRepository.findWindowedStreaks(groupId, from, to)) {
            results.computeIfAbsent(row.getUserId(), key -> new ArrayList<>()).add(row.isWinner());
        }
        Map<UUID, int[]> streaks = new HashMap<>();
        for (Map.Entry<UUID, List<Boolean>> entry : results.entrySet()) {
            int running = 0;
            int best = 0;
            List<Boolean> playerResults = entry.getValue();
            for (boolean win : playerResults) {
                if (win) {
                    running++;
                    best = Math.max(best, running);
                } else {
                    running = 0;
                }
            }
            boolean lastWin = playerResults.get(playerResults.size() - 1);
            int trailing = 0;
            for (int i = playerResults.size() - 1; i >= 0 && playerResults.get(i) == lastWin; i--) {
                trailing++;
            }
            streaks.put(entry.getKey(), new int[] {lastWin ? trailing : -trailing, best});
        }
        return streaks;
    }

    /**
     * Every eligible player's last {@link #RECENT_FORM_LIMIT} results within
     * {@code [from, to)}, chronological (oldest first) — see {@link LeaderboardEntryDto}.
     * Either bound may be null for the all-time board. One set-based query for the whole
     * group, never per-player.
     */
    private Map<UUID, List<Boolean>> recentFormByUser(UUID groupId, Instant from, Instant to) {
        Map<UUID, List<Boolean>> form = new HashMap<>();
        for (RecentFormRow row : matchParticipantRepository.findRecentFormForGroup(groupId, from, to, RECENT_FORM_LIMIT)) {
            form.computeIfAbsent(row.getUserId(), key -> new ArrayList<>()).add(row.isWinner());
        }
        return form;
    }

    /** Turns a raw row into a wire entry, resolving the user's display fields. */
    private LeaderboardRanker.EntryFactory entryFactory(Map<UUID, User> users, Map<UUID, List<Boolean>> form) {
        return (row, rating, provisional) -> {
            User user = users.get(row.userId());
            return new LeaderboardEntryDto(
                    0, // rank assigned by the ranker after sorting
                    user.getId(),
                    user.getDisplayName(),
                    avatarUrls.resolve(user.getPhotoUrl()),
                    user.getAvatarId(),
                    user.getAvatarColor(),
                    row.gamesPlayed(),
                    row.wins(),
                    row.gamesPlayed() - row.wins(),
                    row.pointsFor(),
                    row.pointsAgainst(),
                    LeaderboardRanker.winRate(row.wins(), row.gamesPlayed()),
                    row.currentStreak(),
                    row.bestStreak(),
                    rating,
                    provisional,
                    form.getOrDefault(row.userId(), List.of()));
        };
    }

    @Transactional(readOnly = true)
    public PlayerStatsDto getPlayerStats(UUID groupId, UUID targetUserId, UUID callerId) {
        membershipGuard.requireActiveMember(groupId, callerId);
        GroupMember target = groupMemberRepository
                .findByGroupIdAndUserId(groupId, targetUserId)
                .filter(m -> m.getStatus() == MemberStatus.ACTIVE && m.getRole() != GroupRole.GUEST)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND, "MEMBER_NOT_FOUND", "Player is not a member of this group"));
        User user = target.getUser();

        MemberStats stats = memberStatsRepository
                .findByGroupIdAndUserId(groupId, targetUserId)
                .orElseGet(() -> new MemberStats(target.getGroup(), user));

        List<MatchSummaryDto> recentMatches = matchService.findRecentMatches(groupId, targetUserId, RECENT_MATCHES_LIMIT);

        return new PlayerStatsDto(
                user.getId(),
                user.getDisplayName(),
                avatarUrls.resolve(user.getPhotoUrl()),
                user.getAvatarId(),
                user.getAvatarColor(),
                stats.getMatchesPlayed(),
                stats.getWins(),
                stats.getLosses(),
                stats.getPointsFor(),
                stats.getPointsAgainst(),
                stats.getWinRate() != null ? stats.getWinRate() : BigDecimal.ZERO,
                stats.getCurrentStreak(),
                stats.getBestStreak(),
                recentMatches,
                monthlyTrophyService.forPlayer(groupId, user),
                monthlyFinishes(groupId, targetUserId));
    }

    private List<MonthlyFinishDto> monthlyFinishes(UUID groupId, UUID userId) {
        List<MonthlyFinishDto> finishes = monthlyStandingRepository
                .findLatestFinishes(groupId, userId).stream()
                .map(row -> new MonthlyFinishDto(
                        YearMonth.from(row.getMonth()).toString(), row.getRank(), row.getQualifiedPlayers()))
                .collect(Collectors.toCollection(ArrayList::new));
        Collections.reverse(finishes);
        return finishes;
    }

    /**
     * The player's match-activity instants in {@code [from, to)}, backing the Profile
     * attendance calendar. Same access rules as {@link #getPlayerStats}: the caller must
     * be an active member, and the target an active non-guest member.
     */
    @Transactional(readOnly = true)
    public PlayerAttendanceDto getAttendance(
            UUID groupId, UUID targetUserId, UUID callerId, Instant from, Instant to) {
        membershipGuard.requireActiveMember(groupId, callerId);
        groupMemberRepository
                .findByGroupIdAndUserId(groupId, targetUserId)
                .filter(m -> m.getStatus() == MemberStatus.ACTIVE && m.getRole() != GroupRole.GUEST)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND, "MEMBER_NOT_FOUND", "Player is not a member of this group"));
        return new PlayerAttendanceDto(
                matchParticipantRepository.findPlayerActivity(groupId, targetUserId, from, to));
    }

    /**
     * Every partner this player has had in the group, most games together first
     * (ties broken by win rate together, descending). Fetched on demand — it's
     * behind its own endpoint, not bundled into {@link #getPlayerStats}, so a
     * client can defer the query until the player actually opens the list.
     */
    @Transactional(readOnly = true)
    public List<PartnerDto> getPartners(UUID groupId, UUID targetUserId, UUID callerId) {
        return getPartners(groupId, targetUserId, callerId, null, null);
    }

    @Transactional(readOnly = true)
    public List<PartnerDto> getPartners(
            UUID groupId, UUID targetUserId, UUID callerId, Instant from, Instant to) {
        membershipGuard.requireActiveMember(groupId, callerId);
        groupMemberRepository
                .findByGroupIdAndUserId(groupId, targetUserId)
                .filter(m -> m.getStatus() == MemberStatus.ACTIVE && m.getRole() != GroupRole.GUEST)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND, "MEMBER_NOT_FOUND", "Player is not a member of this group"));

        if ((from == null) != (to == null)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "PARTNER_WINDOW_INCOMPLETE",
                    "Both from and to are required when filtering partners by time");
        }
        List<PartnerRow> rows = from == null
                ? matchParticipantRepository.findPartnerHistoryUnbounded(groupId, targetUserId)
                : matchParticipantRepository.findPartnerHistoryInWindow(groupId, targetUserId, from, to);

        // Guest fillers aren't real partners — leave them out of the tally so a
        // one-off guest can never surface in the partner list.
        Set<UUID> guestIds = groupMemberRepository
                .findByGroupIdAndStatusAndRole(groupId, MemberStatus.ACTIVE, GroupRole.GUEST)
                .stream()
                .map(m -> m.getUser().getId())
                .collect(Collectors.toSet());

        Map<UUID, int[]> tally = new HashMap<>();
        for (PartnerRow row : rows) {
            if (guestIds.contains(row.getPartnerId())) {
                continue;
            }
            int[] counts = tally.computeIfAbsent(row.getPartnerId(), k -> new int[2]);
            counts[0]++;
            if (row.isWinner()) {
                counts[1]++;
            }
        }

        List<PartnerDto> partners = new ArrayList<>();
        for (Map.Entry<UUID, int[]> entry : tally.entrySet()) {
            int[] counts = entry.getValue();
            User partner = groupMemberRepository
                    .findByGroupIdAndUserId(groupId, entry.getKey())
                    .orElseThrow()
                    .getUser();
            BigDecimal winRate = BigDecimal.valueOf(counts[1])
                    .divide(BigDecimal.valueOf(counts[0]), 4, RoundingMode.HALF_UP);
            partners.add(new PartnerDto(
                    partner.getId(),
                    partner.getDisplayName(),
                    partner.getAvatarId(),
                    partner.getAvatarColor(),
                    counts[0],
                    counts[1],
                    winRate));
        }
        partners.sort(Comparator.comparing(PartnerDto::gamesTogether)
                .thenComparing(PartnerDto::winRate)
                .reversed());
        return partners;
    }
}
