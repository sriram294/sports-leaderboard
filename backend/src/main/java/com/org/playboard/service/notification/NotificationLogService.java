package com.org.playboard.service.notification;

import com.org.playboard.entity.notification.NotificationLog;
import com.org.playboard.repository.notification.NotificationLogRepository;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Guards against sending the same notification to the same user twice. */
@Service
public class NotificationLogService {

    private static final Logger log = LoggerFactory.getLogger(NotificationLogService.class);

    private final NotificationLogRepository repository;

    public NotificationLogService(NotificationLogRepository repository) {
        this.repository = repository;
    }

    /**
     * Reserves the right to send {@code (category, dedupeKey)} to this user, returning
     * {@code false} if it has already been sent. Callers must claim <em>before</em>
     * sending: the database, not a prior read, is what makes this safe against a
     * redeploy mid-job or a second instance running the same scheduled work.
     *
     * <p>Runs in its own transaction so the unique violation rolls back only the
     * failed claim, and flushes inside the try so that violation surfaces here rather
     * than at the caller's commit.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean claim(UUID userId, NotificationCategory category, String dedupeKey) {
        try {
            repository.saveAndFlush(new NotificationLog(userId, category, dedupeKey));
            return true;
        } catch (DataIntegrityViolationException alreadySent) {
            log.debug("Already sent {} '{}' to user {}; skipping.", category, dedupeKey, userId);
            return false;
        }
    }
}
