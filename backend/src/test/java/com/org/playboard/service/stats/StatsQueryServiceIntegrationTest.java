package com.org.playboard.service.stats;

import static org.assertj.core.api.Assertions.assertThat;

import com.org.playboard.dto.match.RecordMatchRequest;
import com.org.playboard.dto.match.RecordMatchRequest.SetInput;
import com.org.playboard.dto.match.RecordMatchRequest.TeamInput;
import com.org.playboard.dto.stats.LeaderboardEntryDto;
import com.org.playboard.dto.stats.LeaderboardResponse;
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
import java.time.Instant;
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
    void leaderboardOrdersByWinRateThenWins() {
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
