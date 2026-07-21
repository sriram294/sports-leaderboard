package com.org.playboard.service.stats;

import static org.assertj.core.api.Assertions.assertThat;

import com.org.playboard.dto.match.RecordMatchRequest;
import com.org.playboard.dto.match.RecordMatchRequest.SetInput;
import com.org.playboard.dto.match.RecordMatchRequest.TeamInput;
import com.org.playboard.entity.group.Group;
import com.org.playboard.entity.group.GroupMember;
import com.org.playboard.entity.group.GroupRole;
import com.org.playboard.entity.group.MemberStatus;
import com.org.playboard.entity.sport.Sport;
import com.org.playboard.entity.stats.MonthlyTrophy;
import com.org.playboard.entity.user.User;
import com.org.playboard.repository.group.GroupMemberRepository;
import com.org.playboard.repository.group.GroupRepository;
import com.org.playboard.repository.sport.SportRepository;
import com.org.playboard.repository.stats.MonthlyTrophyRepository;
import com.org.playboard.repository.user.UserRepository;
import com.org.playboard.service.match.MatchService;
import com.org.playboard.service.notification.NotificationLogService;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

/**
 * End-to-end award: real matches in, a crown out.
 *
 * <p>This is the test that matters most for this feature. The first real award does not
 * happen until the month turns, so without driving the whole chain here — matches →
 * windowed standings → claim → stored trophy — a wiring mistake would stay invisible until
 * the morning nobody gets crowned, with no signal that anything was meant to happen.
 *
 * <p>{@code now} is injected rather than taken from the clock so a completed month exists
 * without waiting for one.
 */
@SpringBootTest
@Transactional
class MonthlyTrophyJobIntegrationTest {

    /** Well inside June, so the month is unambiguous in any nearby timezone. */
    private static final Instant JUNE_MATCH = Instant.parse("2026-06-15T10:00:00Z");

    private static final Instant MID_JULY = Instant.parse("2026-07-21T10:00:00Z");

    // The real claim runs REQUIRES_NEW and commits independently, so inside this rolled-back
    // test transaction it cannot see the users created above and trips the notification_log
    // foreign key. Mocking it keeps the announce path exercised without that; the claim's own
    // idempotency is covered by NotificationLogServiceIntegrationTest, which forgoes
    // @Transactional precisely because of this.
    @MockitoBean private NotificationLogService notificationLog;

    @Autowired private MonthlyTrophyJob job;
    @Autowired private MonthlyTrophyRepository trophyRepository;
    @Autowired private MatchService matchService;
    @Autowired private UserRepository userRepository;
    @Autowired private GroupRepository groupRepository;
    @Autowired private GroupMemberRepository groupMemberRepository;
    @Autowired private SportRepository sportRepository;

    @Test
    void aCompletedMonthCrownsItsTopPlayer() {
        Fixture f = newFixture();
        // Team 1 (raj + dev) take five of six; enough games that nobody is provisional.
        playMatches(f, 5, 1);
        playMatches(f, 1, 2);

        job.processGroup(f.group.getId(), JUNE_MATCH, MID_JULY);

        List<MonthlyTrophy> awarded = trophyRepository.findByGroupIdAndUserIdIsNotNullOrderByMonthDesc(
                f.group.getId());
        assertThat(awarded).hasSize(1);
        MonthlyTrophy june = awarded.getFirst();
        assertThat(june.getMonth()).isEqualTo(LocalDate.of(2026, 6, 1));
        assertThat(june.getUserId()).isIn(f.raj.getId(), f.dev.getId());
        assertThat(june.getGamesPlayed()).isEqualTo(6);
        assertThat(june.getWins()).isEqualTo(5);
        assertThat(june.getRating()).isNotNull();
    }

    @Test
    void theInProgressMonthIsNotAwarded() {
        Fixture f = newFixture();
        playMatches(f, 6, 1);

        // "now" sits inside June, so June is still being played.
        job.processGroup(f.group.getId(), JUNE_MATCH, Instant.parse("2026-06-20T10:00:00Z"));

        assertThat(trophyRepository.findDecidedMonths(f.group.getId())).isEmpty();
    }

    @Test
    void reRunningTheScanChangesNothing() {
        Fixture f = newFixture();
        playMatches(f, 5, 1);
        playMatches(f, 1, 2);

        job.processGroup(f.group.getId(), JUNE_MATCH, MID_JULY);
        UUID firstWinner = trophyRepository
                .findByGroupIdAndUserIdIsNotNullOrderByMonthDesc(f.group.getId())
                .getFirst()
                .getUserId();

        // The job re-examines every group on every tick; a second pass must be inert.
        job.processGroup(f.group.getId(), JUNE_MATCH, MID_JULY);
        job.processGroup(f.group.getId(), JUNE_MATCH, MID_JULY);

        List<MonthlyTrophy> awarded =
                trophyRepository.findByGroupIdAndUserIdIsNotNullOrderByMonthDesc(f.group.getId());
        assertThat(awarded).hasSize(1);
        assertThat(awarded.getFirst().getUserId()).isEqualTo(firstWinner);
    }

    @Test
    void aMonthWithNoEligiblePlayerIsDecidedWithNoWinner() {
        // The reachable no-winner case, and it is NOT "too few games": the ranking threshold
        // slides with the group's median, so the median player always clears it and any month
        // with play among eligible members has a winner. What produces an empty month is
        // nobody *eligible* — every player having since left the group, or only guests
        // playing. Here the roster empties after the matches were recorded.
        Fixture f = newFixture();
        playMatches(f, 4, 1);
        groupMemberRepository.findByGroupId(f.group.getId()).forEach(member -> {
            member.setStatus(MemberStatus.REMOVED);
            groupMemberRepository.save(member);
        });

        job.processGroup(f.group.getId(), JUNE_MATCH, MID_JULY);

        assertThat(trophyRepository.findDecidedMonths(f.group.getId()))
                .containsExactly(LocalDate.of(2026, 6, 1));
        assertThat(trophyRepository.findByGroupIdAndUserIdIsNotNullOrderByMonthDesc(f.group.getId()))
                .isEmpty();
    }

    private void playMatches(Fixture f, int count, int winningTeamNo) {
        for (int i = 0; i < count; i++) {
            matchService.createMatch(
                    f.group.getId(),
                    f.raj.getId(),
                    new RecordMatchRequest(
                            JUNE_MATCH.plusSeconds(i * 3600L),
                            List.of(
                                    new TeamInput((short) 1, List.of(f.raj.getId(), f.dev.getId())),
                                    new TeamInput((short) 2, List.of(f.marcus.getId(), f.kiran.getId()))),
                            List.of(winningTeamNo == 1
                                    ? new SetInput((short) 1, (short) 21, (short) 12)
                                    : new SetInput((short) 1, (short) 12, (short) 21)),
                            (short) winningTeamNo));
        }
    }

    private Fixture newFixture() {
        Sport sport = sportRepository.findByCode("badminton_doubles").orElseThrow();

        User raj = userRepository.save(newUser());
        User dev = userRepository.save(newUser());
        User marcus = userRepository.save(newUser());
        User kiran = userRepository.save(newUser());

        Group group = new Group();
        group.setSport(sport);
        group.setName("Trophy Job Group");
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

    private static User newUser() {
        User user = new User();
        user.setEmail("trophy-job-" + UUID.randomUUID().toString().toLowerCase(Locale.ROOT) + "@example.com");
        user.setDisplayName("Player");
        user.setAvatarColor("#7ED321");
        return user;
    }

    private record Fixture(Group group, User raj, User dev, User marcus, User kiran) {}
}
