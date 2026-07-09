package com.org.playboard.entity.stats;

import com.org.playboard.entity.group.Group;
import com.org.playboard.entity.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.generator.EventType;

@Entity
@Table(name = "member_stats")
public class MemberStats {

    @EmbeddedId
    private MemberStatsId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("groupId")
    @JoinColumn(name = "group_id", nullable = false)
    private Group group;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("userId")
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "matches_played", nullable = false)
    private int matchesPlayed;

    @Column(name = "wins", nullable = false)
    private int wins;

    @Column(name = "losses", nullable = false)
    private int losses;

    @Column(name = "points_for", nullable = false)
    private int pointsFor;

    @Column(name = "points_against", nullable = false)
    private int pointsAgainst;

    @Column(name = "current_streak", nullable = false)
    private int currentStreak;

    @Column(name = "best_streak", nullable = false)
    private int bestStreak;

    // DB-generated (stored, computed from wins/matches_played) — never written
    // by the app; @Generated tells Hibernate to SELECT it back after each
    // insert/update instead of leaving the Java field null.
    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(name = "win_rate", insertable = false, updatable = false)
    private BigDecimal winRate;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public MemberStats() {
    }

    public MemberStats(Group group, User user) {
        this.id = new MemberStatsId(group.getId(), user.getId());
        this.group = group;
        this.user = user;
    }

    public MemberStatsId getId() {
        return id;
    }

    public Group getGroup() {
        return group;
    }

    public User getUser() {
        return user;
    }

    public int getMatchesPlayed() {
        return matchesPlayed;
    }

    public void setMatchesPlayed(int matchesPlayed) {
        this.matchesPlayed = matchesPlayed;
    }

    public int getWins() {
        return wins;
    }

    public void setWins(int wins) {
        this.wins = wins;
    }

    public int getLosses() {
        return losses;
    }

    public void setLosses(int losses) {
        this.losses = losses;
    }

    public int getPointsFor() {
        return pointsFor;
    }

    public void setPointsFor(int pointsFor) {
        this.pointsFor = pointsFor;
    }

    public int getPointsAgainst() {
        return pointsAgainst;
    }

    public void setPointsAgainst(int pointsAgainst) {
        this.pointsAgainst = pointsAgainst;
    }

    public int getCurrentStreak() {
        return currentStreak;
    }

    public void setCurrentStreak(int currentStreak) {
        this.currentStreak = currentStreak;
    }

    public int getBestStreak() {
        return bestStreak;
    }

    public void setBestStreak(int bestStreak) {
        this.bestStreak = bestStreak;
    }

    public BigDecimal getWinRate() {
        return winRate;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
