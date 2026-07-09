package com.org.playboard.entity.match;

import com.org.playboard.entity.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "match_events")
public class MatchEvent {

    @Id
    @GeneratedValue
    @Column(name = "id")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "match_id", nullable = false)
    private Match match;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // Mapped via MatchActionConverter (autoApply) — no @Enumerated, see GroupMember.role.
    @Column(name = "action", nullable = false)
    private MatchAction action;

    // Full match state at this point, for diffing/undo later — stored as raw
    // JSON text rather than a Java model since nothing reads it back yet.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "snapshot", columnDefinition = "jsonb")
    private String snapshot;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public UUID getId() {
        return id;
    }

    public Match getMatch() {
        return match;
    }

    public void setMatch(Match match) {
        this.match = match;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public MatchAction getAction() {
        return action;
    }

    public void setAction(MatchAction action) {
        this.action = action;
    }

    public String getSnapshot() {
        return snapshot;
    }

    public void setSnapshot(String snapshot) {
        this.snapshot = snapshot;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
