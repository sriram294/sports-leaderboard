package com.org.playboard.service.stats;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.org.playboard.common.ApiException;
import com.org.playboard.dto.match.RecordMatchRequest;
import com.org.playboard.dto.match.RecordMatchRequest.SetInput;
import com.org.playboard.dto.match.RecordMatchRequest.TeamInput;
import com.org.playboard.dto.stats.LeaderboardEntryDto;
import com.org.playboard.dto.stats.LeaderboardResponse;
import com.org.playboard.dto.stats.PlayerAttendanceDto;
import com.org.playboard.dto.stats.PlayerStatsDto;
import com.org.playboard.entity.group.Group;
import com.org.playboard.entity.group.GroupMember;
import com.org.playboard.entity.group.GroupRole;
import com.org.playboard.entity.group.MemberStatus;
import com.org.playboard.entity.sport.Sport;
import com.org.playboard.entity.user.User;
import com.org.playboard.repository.group.GroupMemberRepository;
import com.org.playboard.repository.group.GroupRepository;
import com.org.playboard.repository.sport.SportRepository;
import com.org.playboard.repository.user.UserRepository;
import com.org.playboard.service.match.MatchService;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

// Live-DB test covering the leaderboard ranking order, own/tapped player
// stats (including the zero-matches case), and the on-demand Best Partner
// computation across multiple partners.
@SpringBootTest
@Transactional
class StatsQueryServiceIntegrationTest {

    @Autowired private StatsQueryService statsQueryService;
    @Autowired private MatchService matchService;
    @Autowired private UserRepository userRepository;
    @Autowired private GroupRepository groupRepository;
    @Autowired private GroupMemberRepository groupMemberRepository;
    @Autowired private SportRepository sportRepository;

    @Test
    void leaderboardOrdersByWinRateThenPointsDifference() {
        Fixture f = newFixture();
        playThreeMatches(f);

        LeaderboardResponse leaderboard = statsQueryService.getLeaderboard(f.group.getId(), f.raj.getId());

        assertThat(leaderboard.rankings()).extracting(LeaderboardEntryDto::userId)
                .containsExactly(f.dev.getId(), f.raj.getId(), f.marcus.getId(), f.kiran.getId());
        assertThat(leaderboard.rankings()).extracting(LeaderboardEntryDto::rank).containsExactly(1, 2, 3, 4);

        LeaderboardEntryDto devEntry = leaderboard.rankings().get(0);
        assertThat(devEntry.wins()).isEqualTo(3);
        assertThat(devEntry.losses()).isZero();
        assertThat(devEntry.winRate()).isEqualByComparingTo("1.0000");
        // Dev won all three in a row → current and best win streak are both 3.
        assertThat(devEntry.currentStreak()).isEqualTo(3);
        assertThat(devEntry.bestStreak()).isEqualTo(3);

        // A member who never played doesn't clutter the leaderboard.
        assertThat(leaderboard.rankings()).extracting(LeaderboardEntryDto::userId)
                .doesNotContain(f.newbie.getId());
    }

    @Test
    void playerStatsIncludesBestPartnerAndRecentMatches() {
        Fixture f = newFixture();
        playThreeMatches(f);

        PlayerStatsDto rajStats = statsQueryService.getPlayerStats(f.group.getId(), f.raj.getId(), f.dev.getId());

        assertThat(rajStats.matchesPlayed()).isEqualTo(3);
        assertThat(rajStats.wins()).isEqualTo(2);
        assertThat(rajStats.losses()).isEqualTo(1);
        assertThat(rajStats.winRate()).isEqualByComparingTo("0.6667");

        // raj partnered with dev twice (2-0) and kiran once (0-1) — dev wins on win rate.
        assertThat(rajStats.bestPartner()).isNotNull();
        assertThat(rajStats.bestPartner().userId()).isEqualTo(f.dev.getId());
        assertThat(rajStats.bestPartner().gamesTogether()).isEqualTo(2);
        assertThat(rajStats.bestPartner().winsTogether()).isEqualTo(2);
        assertThat(rajStats.bestPartner().winRate()).isEqualByComparingTo("1.0000");

        assertThat(rajStats.recentMatches()).hasSize(3);
        // Newest first.
        assertThat(rajStats.recentMatches().get(0).playedAt())
                .isAfterOrEqualTo(rajStats.recentMatches().get(1).playedAt());
    }

    @Test
    void playerStatsForSomeoneWithNoMatchesIsAllZero() {
        Fixture f = newFixture();
        playThreeMatches(f);

        PlayerStatsDto newbieStats = statsQueryService.getPlayerStats(f.group.getId(), f.newbie.getId(), f.raj.getId());

        assertThat(newbieStats.matchesPlayed()).isZero();
        assertThat(newbieStats.winRate()).isEqualByComparingTo("0");
        assertThat(newbieStats.bestPartner()).isNull();
        assertThat(newbieStats.recentMatches()).isEmpty();
    }

    /**
     * Everyone ends on exactly 50%, so the win-rate key can't separate anyone and the
     * points difference has to. Carl and Dina take the most wins (2 apiece) but the
     * worst difference — under the old "win rate, then wins" rule they'd have led the
     * board; ranking last is what proves difference now outranks wins.
     */
    @Test
    void pointsDifferenceBreaksWinRateTiesAheadOfWins() {
        Sport sport = sportRepository.findByCode("badminton_doubles").orElseThrow();
        User ann = userRepository.save(newUser("ann"));
        User bob = userRepository.save(newUser("bob"));
        User carl = userRepository.save(newUser("carl"));
        User dina = userRepository.save(newUser("dina"));
        User eve = userRepository.save(newUser("eve"));
        User finn = userRepository.save(newUser("finn"));

        Group group = new Group();
        group.setSport(sport);
        group.setName("Tiebreak Test Group");
        group.setCreatedBy(ann);
        group.setAvatarColor("#7ED321");
        group = groupRepository.save(group);
        addMember(group, ann, GroupRole.OWNER);
        for (User u : List.of(bob, carl, dina, eve, finn)) {
            addMember(group, u, GroupRole.MEMBER);
        }

        // ann+bob thrash carl+dina, then lose narrowly: 1W-1L, diff +14.
        recordMatch(group, ann, List.of(ann, bob), List.of(carl, dina), 21, 5, 1);
        recordMatch(group, ann, List.of(carl, dina), List.of(ann, bob), 21, 19, 1);
        // eve+finn trade narrow results with carl+dina: 1W-1L, diff 0.
        recordMatch(group, ann, List.of(eve, finn), List.of(carl, dina), 21, 19, 1);
        recordMatch(group, ann, List.of(carl, dina), List.of(eve, finn), 21, 19, 1);
        // carl+dina finish 2W-2L, diff -14 — most wins, worst difference.

        LeaderboardResponse leaderboard = statsQueryService.getLeaderboard(group.getId(), ann.getId());

        assertThat(leaderboard.rankings())
                .allSatisfy(entry -> assertThat(entry.winRate()).isEqualByComparingTo("0.5000"));

        List<UUID> order = leaderboard.rankings().stream().map(LeaderboardEntryDto::userId).toList();
        assertThat(order).hasSize(6);
        // Pairs share identical stats, so their internal order is the arbitrary-but-stable id key.
        assertThat(order.subList(0, 2)).containsExactlyInAnyOrder(ann.getId(), bob.getId());
        assertThat(order.subList(2, 4)).containsExactlyInAnyOrder(eve.getId(), finn.getId());
        assertThat(order.subList(4, 6)).containsExactlyInAnyOrder(carl.getId(), dina.getId());

        LeaderboardEntryDto annEntry = leaderboard.rankings().get(0);
        assertThat(annEntry.pointsFor()).isEqualTo(40);
        assertThat(annEntry.pointsAgainst()).isEqualTo(26);

        LeaderboardEntryDto lastEntry = leaderboard.rankings().get(5);
        assertThat(lastEntry.wins()).isEqualTo(2);
        assertThat(lastEntry.pointsFor() - lastEntry.pointsAgainst()).isEqualTo(-14);
    }

    /** Ordering is fully determined, so repeated reads of a tied board can't shuffle. */
    @Test
    void tiedRowsComeBackInAStableOrder() {
        Fixture f = newFixture();
        playThreeMatches(f);

        List<UUID> first = statsQueryService.getLeaderboard(f.group.getId(), f.raj.getId()).rankings().stream()
                .map(LeaderboardEntryDto::userId)
                .toList();
        List<UUID> second = statsQueryService.getLeaderboard(f.group.getId(), f.raj.getId()).rankings().stream()
                .map(LeaderboardEntryDto::userId)
                .toList();

        assertThat(second).containsExactlyElementsOf(first);
    }

    /**
     * The windowed leaderboard aggregates only matches whose {@code playedAt} falls in
     * {@code [from, to)}; matches outside the window (and all-time streaks) don't count.
     */
    @Test
    void windowedLeaderboardCountsOnlyMatchesInTheCalendarWindow() {
        Fixture f = newFixture();
        ZoneId zone = ZoneId.of("UTC");
        LocalDate firstOfMonth = LocalDate.now(zone).withDayOfMonth(1);
        Instant from = firstOfMonth.atStartOfDay(zone).toInstant();
        Instant to = firstOfMonth.plusMonths(1).atStartOfDay(zone).toInstant();
        Instant inWindow = from.plus(Duration.ofHours(1));
        Instant lastMonth = firstOfMonth.minusDays(5).atStartOfDay(zone).toInstant();

        // This month: (raj, dev) beat (marcus, kiran) 21-12.
        recordMatch(f.group, f.raj, List.of(f.raj, f.dev), List.of(f.marcus, f.kiran), 21, 12, 1, inWindow);
        // Last month: (marcus, kiran) beat (raj, dev) 21-15 — must not count this month.
        recordMatch(f.group, f.raj, List.of(f.marcus, f.kiran), List.of(f.raj, f.dev), 21, 15, 1, lastMonth);

        LeaderboardResponse windowed = statsQueryService.getLeaderboard(f.group.getId(), f.raj.getId(), from, to);

        // Only the in-window match is aggregated: every player has exactly one game.
        assertThat(windowed.rankings()).extracting(LeaderboardEntryDto::userId)
                .containsExactlyInAnyOrder(f.raj.getId(), f.dev.getId(), f.marcus.getId(), f.kiran.getId());
        assertThat(windowed.rankings()).allSatisfy(e -> assertThat(e.gamesPlayed()).isEqualTo(1));
        assertThat(windowed.rankings()).extracting(LeaderboardEntryDto::rank).containsExactly(1, 2, 3, 4);

        LeaderboardEntryDto raj = entryFor(windowed, f.raj.getId());
        assertThat(raj.wins()).isEqualTo(1);
        assertThat(raj.losses()).isZero();
        assertThat(raj.winRate()).isEqualByComparingTo("1.0000");
        assertThat(raj.pointsFor()).isEqualTo(21);
        assertThat(raj.pointsAgainst()).isEqualTo(12);
        // Streaks are all-time only, so windowed entries report zero.
        assertThat(raj.currentStreak()).isZero();
        assertThat(raj.bestStreak()).isZero();

        LeaderboardEntryDto marcus = entryFor(windowed, f.marcus.getId());
        assertThat(marcus.wins()).isZero();
        assertThat(marcus.losses()).isEqualTo(1);
        assertThat(marcus.pointsFor()).isEqualTo(12);

        // Winners (win rate 1.0) rank above losers; pairs share the stable id key.
        List<UUID> order = windowed.rankings().stream().map(LeaderboardEntryDto::userId).toList();
        assertThat(order.subList(0, 2)).containsExactlyInAnyOrder(f.raj.getId(), f.dev.getId());
        assertThat(order.subList(2, 4)).containsExactlyInAnyOrder(f.marcus.getId(), f.kiran.getId());

        // Sanity: all-time (no window) sees both matches — dev has played twice.
        LeaderboardResponse allTime = statsQueryService.getLeaderboard(f.group.getId(), f.raj.getId());
        assertThat(entryFor(allTime, f.dev.getId()).gamesPlayed()).isEqualTo(2);
    }

    /**
     * Attendance returns the player's match instants inside {@code [from, to)} only —
     * out-of-window matches are excluded, and two matches on the same day both come back
     * (the client buckets instants into local days).
     */
    @Test
    void attendanceReturnsInWindowMatchInstantsOnly() {
        Fixture f = newFixture();
        ZoneId zone = ZoneId.of("UTC");
        LocalDate firstOfMonth = LocalDate.now(zone).withDayOfMonth(1);
        Instant from = firstOfMonth.atStartOfDay(zone).toInstant();
        Instant to = firstOfMonth.plusMonths(1).atStartOfDay(zone).toInstant();

        Instant dayOneMorning = from.plus(Duration.ofHours(2));
        Instant dayOneEvening = from.plus(Duration.ofHours(9)); // same day, distinct instant
        Instant dayFour = from.plus(Duration.ofDays(3)).plus(Duration.ofHours(1));
        Instant lastMonth = firstOfMonth.minusDays(5).atStartOfDay(zone).toInstant();

        recordMatch(f.group, f.raj, List.of(f.raj, f.dev), List.of(f.marcus, f.kiran), 21, 12, 1, dayOneMorning);
        recordMatch(f.group, f.raj, List.of(f.raj, f.dev), List.of(f.marcus, f.kiran), 21, 15, 1, dayOneEvening);
        recordMatch(f.group, f.raj, List.of(f.raj, f.dev), List.of(f.marcus, f.kiran), 21, 10, 1, dayFour);
        recordMatch(f.group, f.raj, List.of(f.raj, f.dev), List.of(f.marcus, f.kiran), 21, 9, 1, lastMonth);

        PlayerAttendanceDto attendance =
                statsQueryService.getAttendance(f.group.getId(), f.raj.getId(), f.dev.getId(), from, to);

        assertThat(attendance.playedAt())
                .containsExactly(dayOneMorning, dayOneEvening, dayFour) // sorted asc, last-month excluded
                .doesNotContain(lastMonth);

        // A player who didn't play in the window gets an empty list, not an error.
        PlayerAttendanceDto newbie =
                statsQueryService.getAttendance(f.group.getId(), f.newbie.getId(), f.raj.getId(), from, to);
        assertThat(newbie.playedAt()).isEmpty();
    }

    /** Attendance is gated like stats: a non-member target is rejected. */
    @Test
    void attendanceRejectsNonMemberTarget() {
        Fixture f = newFixture();
        User outsider = userRepository.save(newUser("outsider")); // saved but never added to the group
        Instant from = Instant.now().minus(Duration.ofDays(30));
        Instant to = Instant.now();

        assertThatThrownBy(
                        () -> statsQueryService.getAttendance(f.group.getId(), outsider.getId(), f.raj.getId(), from, to))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("not a member");
    }

    private static LeaderboardEntryDto entryFor(LeaderboardResponse leaderboard, UUID userId) {
        return leaderboard.rankings().stream()
                .filter(e -> e.userId().equals(userId))
                .findFirst()
                .orElseThrow();
    }

    private void recordMatch(Group group, User recorder, List<User> team1, List<User> team2, int t1, int t2, int winner) {
        recordMatch(group, recorder, team1, team2, t1, t2, winner, Instant.now());
    }

    private void recordMatch(
            Group group, User recorder, List<User> team1, List<User> team2, int t1, int t2, int winner, Instant playedAt) {
        matchService.createMatch(
                group.getId(),
                recorder.getId(),
                new RecordMatchRequest(
                        playedAt,
                        List.of(
                                new TeamInput((short) 1, team1.stream().map(User::getId).toList()),
                                new TeamInput((short) 2, team2.stream().map(User::getId).toList())),
                        List.of(new SetInput((short) 1, (short) t1, (short) t2)),
                        (short) winner));
    }

    private void playThreeMatches(Fixture f) {
        // Match 1 & 2: (raj, dev) beat (marcus, kiran).
        for (int i = 0; i < 2; i++) {
            matchService.createMatch(
                    f.group.getId(),
                    f.raj.getId(),
                    new RecordMatchRequest(
                            Instant.now(),
                            List.of(
                                    new TeamInput((short) 1, List.of(f.raj.getId(), f.dev.getId())),
                                    new TeamInput((short) 2, List.of(f.marcus.getId(), f.kiran.getId()))),
                            List.of(new SetInput((short) 1, (short) 21, (short) 12)),
                            (short) 1));
        }

        // Match 3: (raj, kiran) lose to (dev, marcus) — gives raj a second, worse partner.
        matchService.createMatch(
                f.group.getId(),
                f.raj.getId(),
                new RecordMatchRequest(
                        Instant.now(),
                        List.of(
                                new TeamInput((short) 1, List.of(f.raj.getId(), f.kiran.getId())),
                                new TeamInput((short) 2, List.of(f.dev.getId(), f.marcus.getId()))),
                        List.of(new SetInput((short) 1, (short) 10, (short) 21)),
                        (short) 2));
    }

    private Fixture newFixture() {
        Sport sport = sportRepository.findByCode("badminton_doubles").orElseThrow();

        User raj = userRepository.save(newUser("raj"));
        User dev = userRepository.save(newUser("dev"));
        User marcus = userRepository.save(newUser("marcus"));
        User kiran = userRepository.save(newUser("kiran"));
        User newbie = userRepository.save(newUser("newbie"));

        Group group = new Group();
        group.setSport(sport);
        group.setName("Stats Test Group");
        group.setCreatedBy(raj);
        group.setAvatarColor("#7ED321");
        group = groupRepository.save(group);

        addMember(group, raj, GroupRole.OWNER);
        addMember(group, dev, GroupRole.MEMBER);
        addMember(group, marcus, GroupRole.MEMBER);
        addMember(group, kiran, GroupRole.MEMBER);
        addMember(group, newbie, GroupRole.MEMBER);

        return new Fixture(group, raj, dev, marcus, kiran, newbie);
    }

    private void addMember(Group group, User user, GroupRole role) {
        GroupMember member = new GroupMember();
        member.setGroup(group);
        member.setUser(user);
        member.setRole(role);
        member.setStatus(MemberStatus.ACTIVE);
        groupMemberRepository.save(member);
    }

    private static User newUser(String name) {
        User user = new User();
        user.setEmail("stats-test-" + name + "-" + UUID.randomUUID() + "@example.com");
        user.setDisplayName(name);
        user.setAvatarColor("#7ED321");
        return user;
    }

    private record Fixture(Group group, User raj, User dev, User marcus, User kiran, User newbie) {}
}
