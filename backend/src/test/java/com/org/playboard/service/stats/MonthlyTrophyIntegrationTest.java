package com.org.playboard.service.stats;

import static org.assertj.core.api.Assertions.assertThat;

import com.org.playboard.dto.group.CreateGroupRequest;
import com.org.playboard.dto.group.GroupSummaryDto;
import com.org.playboard.dto.stats.MonthlyTrophyDto;
import com.org.playboard.entity.stats.MonthlyTrophy;
import com.org.playboard.entity.user.User;
import com.org.playboard.repository.stats.MonthlyTrophyRepository;
import com.org.playboard.repository.user.UserRepository;
import com.org.playboard.service.group.GroupService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * Live-DB tests for the award claim.
 *
 * <p>The interesting behaviour is entirely in Postgres — the unique index on
 * {@code (group_id, month)} and {@code on conflict do nothing} — so none of it is reachable
 * from a pure unit test. What is being pinned is that a repeat scan is harmless and that a
 * month with no winner is still recorded as decided.
 */
@SpringBootTest
@Transactional
class MonthlyTrophyIntegrationTest {

    private static final LocalDate JUNE = LocalDate.of(2026, 6, 1);

    @Autowired private MonthlyTrophyRepository trophyRepository;
    @Autowired private MonthlyTrophyService monthlyTrophyService;
    @Autowired private GroupService groupService;
    @Autowired private UserRepository userRepository;

    @Test
    void aMonthCanOnlyBeAwardedOnce() {
        User winner = userRepository.save(newUser());
        UUID groupId = newGroup(winner);

        assertThat(award(groupId, winner.getId(), JUNE)).isEqualTo(1);
        // A second sweep over the same month — the index, not a prior read, refuses it.
        assertThat(award(groupId, winner.getId(), JUNE)).isZero();

        assertThat(trophyRepository.findDecidedMonths(groupId)).containsExactly(JUNE);
    }

    @Test
    void aMonthWithNoWinnerIsStillRecordedAsDecided() {
        User member = userRepository.save(newUser());
        UUID groupId = newGroup(member);

        assertThat(award(groupId, null, JUNE)).isEqualTo(1);

        // Decided, so the job stops reconsidering it...
        assertThat(trophyRepository.findDecidedMonths(groupId)).containsExactly(JUNE);
        // ...but it is bookkeeping, and must never reach a client.
        assertThat(trophyRepository.findByGroupIdAndUserIdIsNotNullOrderByMonthDesc(groupId))
                .isEmpty();
        assertThat(monthlyTrophyService.forGroup(groupId, member.getId(), 6)).isEmpty();
    }

    @Test
    void awardedMonthsAreServedNewestFirst() {
        User winner = userRepository.save(newUser());
        UUID groupId = newGroup(winner);

        award(groupId, winner.getId(), LocalDate.of(2026, 4, 1));
        award(groupId, winner.getId(), LocalDate.of(2026, 5, 1));
        award(groupId, null, JUNE); // a quiet month in the middle of the run

        List<MonthlyTrophyDto> roll = monthlyTrophyService.forGroup(groupId, winner.getId(), 6);

        // June is skipped entirely rather than rendered as a gap.
        assertThat(roll).extracting(MonthlyTrophyDto::month).containsExactly("2026-05", "2026-04");
        assertThat(roll.getFirst().displayName()).isEqualTo(winner.getDisplayName());
        assertThat(roll.getFirst().rating()).isEqualByComparingTo("61.2");
    }

    @Test
    void aPlayersOwnTrophiesAreServedNewestFirst() {
        User winner = userRepository.save(newUser());
        User other = userRepository.save(newUser());
        UUID groupId = newGroup(winner);

        award(groupId, winner.getId(), LocalDate.of(2026, 4, 1));
        award(groupId, other.getId(), LocalDate.of(2026, 5, 1));

        assertThat(monthlyTrophyService.forPlayer(groupId, winner))
                .extracting(MonthlyTrophyDto::month)
                .containsExactly("2026-04");
    }

    @Test
    void theStoredSnapshotSurvivesARoundTrip() {
        User winner = userRepository.save(newUser());
        UUID groupId = newGroup(winner);
        award(groupId, winner.getId(), JUNE);

        MonthlyTrophy stored =
                trophyRepository.findByGroupIdAndUserIdOrderByMonthDesc(groupId, winner.getId()).getFirst();

        assertThat(stored.hasWinner()).isTrue();
        assertThat(stored.getMonth()).isEqualTo(JUNE);
        assertThat(stored.getGamesPlayed()).isEqualTo(18);
        assertThat(stored.getWins()).isEqualTo(12);
        assertThat(stored.getCreatedAt()).isNotNull();
    }

    private int award(UUID groupId, UUID userId, LocalDate month) {
        return trophyRepository.awardIfAbsent(
                groupId,
                userId,
                month,
                userId == null ? null : new BigDecimal("61.2"),
                userId == null ? null : 18,
                userId == null ? null : 12);
    }

    private UUID newGroup(User owner) {
        GroupSummaryDto group =
                groupService.createGroup(owner.getId(), new CreateGroupRequest("Trophy Test", "badminton_doubles"));
        return group.id();
    }

    private static User newUser() {
        User user = new User();
        user.setEmail("trophy-" + UUID.randomUUID().toString().toLowerCase(Locale.ROOT) + "@example.com");
        user.setDisplayName("Trophy Tester");
        user.setAvatarColor("#7ED321");
        return user;
    }
}
