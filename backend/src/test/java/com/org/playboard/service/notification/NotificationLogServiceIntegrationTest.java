package com.org.playboard.service.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.org.playboard.entity.user.User;
import com.org.playboard.repository.notification.NotificationLogRepository;
import com.org.playboard.repository.user.UserRepository;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Live-DB test for the idempotency guarantee. Deliberately <em>not</em> {@code @Transactional}:
 * {@code claim} runs {@code REQUIRES_NEW} and commits on its own, which is the whole point —
 * a rolled-back test transaction would prove nothing about what a second instance would see.
 * Rows are cleaned up explicitly instead.
 */
@SpringBootTest
class NotificationLogServiceIntegrationTest {

    @Autowired private NotificationLogService notificationLogService;
    @Autowired private NotificationLogRepository notificationLogRepository;
    @Autowired private UserRepository userRepository;

    private User user;

    @BeforeEach
    void createUser() {
        User newUser = new User();
        newUser.setEmail("notif-log-" + UUID.randomUUID() + "@example.com");
        newUser.setDisplayName("Log Tester");
        newUser.setAvatarColor("#7ED321");
        user = userRepository.save(newUser);
    }

    @AfterEach
    void cleanUp() {
        notificationLogRepository.deleteAll(notificationLogRepository.findAll().stream()
                .filter(row -> row.getUserId().equals(user.getId()))
                .toList());
        userRepository.delete(user);
    }

    @Test
    void theSameNotificationCanOnlyBeClaimedOnce() {
        String key = "rank_change:group-1:2026-07-20T19:00:00Z";

        assertThat(notificationLogService.claim(user.getId(), NotificationCategory.DAILY_SUMMARY, key))
                .isTrue();
        // A second scan of the same ended session — the DB, not a prior read, refuses it.
        assertThat(notificationLogService.claim(user.getId(), NotificationCategory.DAILY_SUMMARY, key))
                .isFalse();
    }

    @Test
    void aDifferentSessionIsClaimedIndependently() {
        assertThat(notificationLogService.claim(
                        user.getId(), NotificationCategory.DAILY_SUMMARY, "rank_change:group-1:monday"))
                .isTrue();
        assertThat(notificationLogService.claim(
                        user.getId(), NotificationCategory.DAILY_SUMMARY, "rank_change:group-1:tuesday"))
                .isTrue();
    }

    @Test
    void theSameKeyUnderADifferentCategoryIsNotBlocked() {
        String key = "shared-key";

        assertThat(notificationLogService.claim(user.getId(), NotificationCategory.DAILY_SUMMARY, key))
                .isTrue();
        assertThat(notificationLogService.claim(user.getId(), NotificationCategory.GROUP_UPDATE, key))
                .isTrue();
    }

    @Test
    void aFailedClaimLeavesTheServiceUsable() {
        String key = "rank_change:group-1:repeat";
        notificationLogService.claim(user.getId(), NotificationCategory.DAILY_SUMMARY, key);
        notificationLogService.claim(user.getId(), NotificationCategory.DAILY_SUMMARY, key);

        // The constraint violation must not have poisoned the persistence context — the
        // caller carries on sending to the rest of the group after one duplicate.
        assertThat(notificationLogService.claim(
                        user.getId(), NotificationCategory.DAILY_SUMMARY, "rank_change:group-1:next"))
                .isTrue();
    }
}
