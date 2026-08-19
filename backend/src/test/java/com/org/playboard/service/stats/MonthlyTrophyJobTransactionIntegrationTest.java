package com.org.playboard.service.stats;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.org.playboard.dto.stats.LeaderboardEntryDto;
import com.org.playboard.entity.group.Group;
import com.org.playboard.entity.sport.Sport;
import com.org.playboard.entity.stats.MonthlyTrophy;
import com.org.playboard.entity.user.User;
import com.org.playboard.repository.group.GroupRepository;
import com.org.playboard.repository.sport.SportRepository;
import com.org.playboard.repository.stats.MonthlyTrophyRepository;
import com.org.playboard.repository.user.UserRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Deliberately <em>not</em> {@code @Transactional}, unlike {@link MonthlyTrophyJobIntegrationTest}.
 *
 * <p>Exercises {@link MonthlyTrophyJob#processGroup} — the same entry point
 * {@code awardCompletedMonths} reaches via a bare {@code this.} call — rather than calling
 * {@code awardMonth} directly. That distinction matters: {@code awardMonth} is only ever
 * reached through self-invocation in production, which Spring's proxy-based AOP does not
 * intercept, so calling it directly on the injected bean would exercise a code path that
 * cannot actually occur and would not have caught the bug this test guards against (a first
 * attempt at this test did exactly that, and passed against broken code).
 *
 * <p>This is what broke the very first live award, at the July→August rollover: every group's
 * scan failed with {@code TransactionRequiredException} because the write in
 * {@code MonthlyTrophyRepository.awardIfAbsent} had no active transaction.
 */
@SpringBootTest
class MonthlyTrophyJobTransactionIntegrationTest {

    private static final Instant JUNE_START = Instant.parse("2026-06-01T00:00:00Z");
    private static final Instant AFTER_JUNE = Instant.parse("2026-07-05T00:00:00Z");

    @Autowired private MonthlyTrophyJob job;
    @Autowired private MonthlyTrophyRepository trophyRepository;
    @Autowired private MonthlyStandingsWriter standingsWriter;
    @Autowired private GroupRepository groupRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private SportRepository sportRepository;

    private User owner;
    private Group group;

    @BeforeEach
    void createGroup() {
        Sport sport = sportRepository.findByCode("badminton_doubles").orElseThrow();

        owner = new User();
        owner.setEmail("trophy-tx-" + UUID.randomUUID() + "@example.com");
        owner.setDisplayName("Owner");
        owner.setAvatarColor("#7ED321");
        owner = userRepository.save(owner);

        group = new Group();
        group.setSport(sport);
        group.setName("Trophy Tx Group");
        group.setCreatedBy(owner);
        group.setAvatarColor("#7ED321");
        group = groupRepository.save(group);
    }

    @AfterEach
    void cleanUp() {
        trophyRepository.deleteAll(trophyRepository.findAll().stream()
                .filter(trophy -> trophy.getGroupId().equals(group.getId()))
                .toList());
        groupRepository.delete(group);
        userRepository.delete(owner);
    }

    @Test
    void scanningAGroupCommitsOutsideAnyCallerTransaction() {
        assertThatCode(() -> job.processGroup(group.getId(), JUNE_START, AFTER_JUNE))
                .doesNotThrowAnyException();

        assertThat(trophyRepository.findDecidedMonths(group.getId()))
                .containsExactly(LocalDate.of(2026, 6, 1));
    }

    @Test
    void reRunningOutsideATransactionStillClaimsOnlyOnce() {
        job.processGroup(group.getId(), JUNE_START, AFTER_JUNE);
        job.processGroup(group.getId(), JUNE_START, AFTER_JUNE);

        MonthlyTrophy stored = trophyRepository.findAll().stream()
                .filter(trophy -> trophy.getGroupId().equals(group.getId()))
                .findFirst()
                .orElseThrow();
        assertThat(stored.hasWinner()).isFalse();
    }

    @Test
    void aSnapshotFailureRollsBackTheTrophyClaim() {
        // Rank zero violates V14's check constraint. flush() happens inside the writer's
        // transaction, proving the earlier trophy insert cannot escape on its own.
        LeaderboardEntryDto invalid = new LeaderboardEntryDto(
                0, owner.getId(), "Owner", null, null, "#7ED321",
                1, 1, 0, 21, 10, BigDecimal.ONE, 0, 0,
                BigDecimal.TEN, false, List.of(true));
        var standings = new LeaderboardRanker.Standings(List.of(invalid), 1);

        assertThatThrownBy(() -> standingsWriter.capture(
                group.getId(), YearMonth.of(2026, 6), standings, Optional.of(invalid)))
                .isInstanceOf(RuntimeException.class);

        assertThat(trophyRepository.findDecidedMonths(group.getId())).isEmpty();
    }
}
