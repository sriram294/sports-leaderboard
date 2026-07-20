package com.org.playboard.entity.notification;

import com.org.playboard.common.Auditable;
import com.org.playboard.service.notification.NotificationCategory;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * A push that has already been sent to one user. Claimed before sending, so a
 * unique violation on {@code (user_id, category, dedupe_key)} is how a repeat send
 * is prevented — see {@code NotificationLogService.claim}.
 *
 * <p>{@code userId} is a plain column rather than a {@code @ManyToOne}: rows are
 * written from post-commit async threads with no open session, and nothing
 * navigates back to the user. {@code getCreatedAt()} is the send time.
 */
@Entity
@Table(name = "notification_log")
public class NotificationLog extends Auditable {

    @Id
    @GeneratedValue
    @Column(name = "id")
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false)
    private NotificationCategory category;

    @Column(name = "dedupe_key", nullable = false)
    private String dedupeKey;

    protected NotificationLog() {
        // for JPA
    }

    public NotificationLog(UUID userId, NotificationCategory category, String dedupeKey) {
        this.userId = userId;
        this.category = category;
        this.dedupeKey = dedupeKey;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public NotificationCategory getCategory() {
        return category;
    }

    public String getDedupeKey() {
        return dedupeKey;
    }
}
