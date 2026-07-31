package com.org.playboard.service.stats;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.org.playboard.entity.group.Group;
import com.org.playboard.entity.sport.Sport;
import com.org.playboard.entity.stats.MonthlyTrophy;
import com.org.playboard.entity.user.User;
import com.org.playboard.repository.group.GroupRepository;
import com.org.playboard.repository.sport.SportRepository;
import com.org.playboard.repository.stats.MonthlyTrophyRepository;
import com.org.playboard.repository.user.UserRepository;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Deliberately <em>not</em> {@code @Transactional}, unlike {@link MonthlyTrophyJobIntegrationTest}.
 *
 * <p>{@code awardMonth} calls the {@code @Modifying} {@code awardIfAbsent} insert, which needs
 * an active transaction. In production that call arrives from the bare {@code @Scheduled}
 * method with no transaction of its own; a test wrapped in {@code @Transactional} supplies one
 * for free and would never catch a missing one on the production method. This is what broke
 * the very first live award (the July→August rollover): every group's scan failed with
 * {@code TransactionRequiredException}.
 */
@SpringBootTest
class MonthlyTrophyJobTransactionIntegrationTest {

    @Autowired private MonthlyTrophyJob job;
    @Autowired private MonthlyTrophyRepository trophyRepository;
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
    void awardingAMonthCommitsOutsideAnyCallerTransaction() {
        assertThatCode(() -> job.awardMonth(group.getId(), YearMonth.of(2026, 6)))
                .doesNotThrowAnyException();

        assertThat(trophyRepository.findDecidedMonths(group.getId()))
                .containsExactly(LocalDate.of(2026, 6, 1));
    }

    @Test
    void reRunningOutsideATransactionStillClaimsOnlyOnce() {
        job.awardMonth(group.getId(), YearMonth.of(2026, 6));
        job.awardMonth(group.getId(), YearMonth.of(2026, 6));

        MonthlyTrophy stored = trophyRepository.findAll().stream()
                .filter(trophy -> trophy.getGroupId().equals(group.getId()))
                .findFirst()
                .orElseThrow();
        assertThat(stored.hasWinner()).isFalse();
    }
}
