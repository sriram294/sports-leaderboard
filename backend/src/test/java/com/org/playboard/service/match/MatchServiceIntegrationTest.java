package com.org.playboard.service.match;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.org.playboard.common.ApiException;
import com.org.playboard.dto.match.MatchDetailDto;
import com.org.playboard.dto.match.MatchListResponse;
import com.org.playboard.dto.match.RecordMatchRequest;
import com.org.playboard.dto.match.RecordMatchRequest.SetInput;
import com.org.playboard.dto.match.RecordMatchRequest.TeamInput;
import com.org.playboard.entity.group.Group;
import com.org.playboard.entity.group.GroupMember;
import com.org.playboard.entity.group.GroupRole;
import com.org.playboard.entity.group.MemberStatus;
import com.org.playboard.entity.sport.Sport;
import com.org.playboard.entity.stats.MemberStats;
import com.org.playboard.entity.user.User;
import com.org.playboard.repository.group.GroupMemberRepository;
import com.org.playboard.repository.group.GroupRepository;
import com.org.playboard.repository.sport.SportRepository;
import com.org.playboard.repository.stats.MemberStatsRepository;
import com.org.playboard.repository.user.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

// Live-DB test covering match record/edit/delete and the member_stats
// recompute it triggers: sums, win/loss streaks (including streak reversal
// on edit and reversion on delete), permission checks, validation errors,
// and cursor pagination.
@SpringBootTest
@Transactional
class MatchServiceIntegrationTest {

    @Autowired private MatchService matchService;
    @Autowired private UserRepository userRepository;
    @Autowired private GroupRepository groupRepository;
    @Autowired private GroupMemberRepository groupMemberRepository;
    @Autowired private SportRepository sportRepository;
    @Autowired private MemberStatsRepository memberStatsRepository;

    @Test
    void recordEditDeleteFlowRecomputesStatsCorrectly() {
        Fixture f = newFixture();

        // Match 1: team1 (raj, dev) beats team2 (marcus, kiran) 21-12, 21-17.
        MatchDetailDto match1 = matchService.createMatch(
                f.group.getId(),
                f.raj.getId(),
                recordRequest(1, List.of(new SetInput((short) 1, (short) 21, (short) 12),
                        new SetInput((short) 2, (short) 21, (short) 17)), f));
        assertThat(match1.teams()).hasSize(2);
        assertThat(match1.teams().get(0).winner()).isTrue();
        assertThat(match1.teams().get(0).players()).extracting("userId")
                .containsExactlyInAnyOrder(f.raj.getId(), f.dev.getId());

        assertStats(f.group, f.raj, 1, 1, 0, 42, 29, 1, 1);
        assertStats(f.group, f.marcus, 1, 0, 1, 29, 42, -1, 0);

        // Match 2: same teams, team2 wins this time.
        MatchDetailDto match2 = matchService.createMatch(
                f.group.getId(),
                f.raj.getId(),
                recordRequest(2, List.of(new SetInput((short) 1, (short) 15, (short) 21),
                        new SetInput((short) 2, (short) 18, (short) 21)), f));

        assertStats(f.group, f.raj, 2, 1, 1, 75, 71, -1, 1);
        assertStats(f.group, f.marcus, 2, 1, 1, 71, 75, 1, 1);

        // A plain member who didn't record match2 (raj did) can't edit it.
        assertThatThrownBy(() -> matchService.updateMatch(
                        f.group.getId(),
                        match2.id(),
                        f.kiran.getId(),
                        recordRequest(1, match2.sets().stream()
                                .map(s -> new SetInput(s.setNo(), s.team1Score(), s.team2Score()))
                                .toList(), f)))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo("MATCH_EDIT_FORBIDDEN"));

        // The recorder edits match2: flip the winner back to team1.
        matchService.updateMatch(
                f.group.getId(),
                match2.id(),
                f.raj.getId(),
                recordRequest(1, List.of(new SetInput((short) 1, (short) 21, (short) 15),
                        new SetInput((short) 2, (short) 21, (short) 18)), f));

        assertStats(f.group, f.raj, 2, 2, 0, 84, 62, 2, 2);
        assertStats(f.group, f.marcus, 2, 0, 2, 62, 84, -2, 0);

        // Deleting match2 reverts stats to the match1-only state.
        matchService.deleteMatch(f.group.getId(), match2.id(), f.raj.getId());
        assertStats(f.group, f.raj, 1, 1, 0, 42, 29, 1, 1);
        assertStats(f.group, f.marcus, 1, 0, 1, 29, 42, -1, 0);
    }

    @Test
    void rejectsWrongTeamSize() {
        Fixture f = newFixture();
        RecordMatchRequest request = new RecordMatchRequest(
                Instant.now(),
                List.of(new TeamInput((short) 1, List.of(f.raj.getId())),
                        new TeamInput((short) 2, List.of(f.marcus.getId(), f.kiran.getId()))),
                List.of(new SetInput((short) 1, (short) 21, (short) 10)),
                (short) 1);

        assertThatThrownBy(() -> matchService.createMatch(f.group.getId(), f.raj.getId(), request))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo("MATCH_INVALID_TEAMS"));
    }

    @Test
    void rejectsPlayerOnBothTeams() {
        Fixture f = newFixture();
        RecordMatchRequest request = new RecordMatchRequest(
                Instant.now(),
                List.of(new TeamInput((short) 1, List.of(f.raj.getId(), f.dev.getId())),
                        new TeamInput((short) 2, List.of(f.raj.getId(), f.kiran.getId()))),
                List.of(new SetInput((short) 1, (short) 21, (short) 10)),
                (short) 1);

        assertThatThrownBy(() -> matchService.createMatch(f.group.getId(), f.raj.getId(), request))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo("MATCH_INVALID_TEAMS"));
    }

    @Test
    void rejectsNegativeScores() {
        Fixture f = newFixture();
        RecordMatchRequest request = new RecordMatchRequest(
                Instant.now(),
                List.of(new TeamInput((short) 1, List.of(f.raj.getId(), f.dev.getId())),
                        new TeamInput((short) 2, List.of(f.marcus.getId(), f.kiran.getId()))),
                List.of(new SetInput((short) 1, (short) -1, (short) 10)),
                (short) 1);

        assertThatThrownBy(() -> matchService.createMatch(f.group.getId(), f.raj.getId(), request))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo("MATCH_INVALID_SCORES"));
    }

    @Test
    void paginatesMatchesByCursor() {
        Fixture f = newFixture();
        for (int i = 0; i < 3; i++) {
            matchService.createMatch(
                    f.group.getId(),
                    f.raj.getId(),
                    recordRequest(1, List.of(new SetInput((short) 1, (short) 21, (short) 10)), f));
        }

        MatchListResponse firstPage = matchService.listMatches(f.group.getId(), f.raj.getId(), null, 2);
        assertThat(firstPage.matches()).hasSize(2);
        assertThat(firstPage.nextCursor()).isNotNull();

        MatchListResponse secondPage =
                matchService.listMatches(f.group.getId(), f.raj.getId(), firstPage.nextCursor(), 2);
        assertThat(secondPage.matches()).hasSize(1);
        assertThat(secondPage.nextCursor()).isNull();
    }

    private void assertStats(
            Group group,
            User user,
            int matchesPlayed,
            int wins,
            int losses,
            int pointsFor,
            int pointsAgainst,
            int currentStreak,
            int bestStreak) {
        MemberStats stats =
                memberStatsRepository.findByGroupIdAndUserId(group.getId(), user.getId()).orElseThrow();
        assertThat(stats.getMatchesPlayed()).as("matchesPlayed").isEqualTo(matchesPlayed);
        assertThat(stats.getWins()).as("wins").isEqualTo(wins);
        assertThat(stats.getLosses()).as("losses").isEqualTo(losses);
        assertThat(stats.getPointsFor()).as("pointsFor").isEqualTo(pointsFor);
        assertThat(stats.getPointsAgainst()).as("pointsAgainst").isEqualTo(pointsAgainst);
        assertThat(stats.getCurrentStreak()).as("currentStreak").isEqualTo(currentStreak);
        assertThat(stats.getBestStreak()).as("bestStreak").isEqualTo(bestStreak);
    }

    private RecordMatchRequest recordRequest(int winningTeamNo, List<SetInput> sets, Fixture f) {
        return new RecordMatchRequest(
                Instant.now(),
                List.of(
                        new TeamInput((short) 1, List.of(f.raj.getId(), f.dev.getId())),
                        new TeamInput((short) 2, List.of(f.marcus.getId(), f.kiran.getId()))),
                sets,
                (short) winningTeamNo);
    }

    private Fixture newFixture() {
        Sport sport = sportRepository.findByCode("badminton_doubles").orElseThrow();

        User raj = userRepository.save(newUser("raj"));
        User dev = userRepository.save(newUser("dev"));
        User marcus = userRepository.save(newUser("marcus"));
        User kiran = userRepository.save(newUser("kiran"));

        Group group = new Group();
        group.setSport(sport);
        group.setName("Match Test Group");
        group.setCreatedBy(raj);
        group.setAvatarColor("#7ED321");
        group = groupRepository.save(group);

        addMember(group, raj, GroupRole.OWNER);
        addMember(group, dev, GroupRole.MEMBER);
        addMember(group, marcus, GroupRole.MEMBER);
        addMember(group, kiran, GroupRole.MEMBER);

        return new Fixture(group, raj, dev, marcus, kiran);
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
        user.setEmail("match-test-" + name + "-" + UUID.randomUUID() + "@example.com");
        user.setDisplayName(name);
        user.setAvatarColor("#7ED321");
        return user;
    }

    private record Fixture(Group group, User raj, User dev, User marcus, User kiran) {}
}
