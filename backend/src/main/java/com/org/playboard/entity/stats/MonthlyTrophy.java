package com.org.playboard.entity.stats;

import com.org.playboard.common.Auditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Who topped a group's leaderboard in one completed calendar month.
 *
 * <p>A frozen snapshot, not a live derivation: the stats here are what they were when the
 * month closed, so a match edited afterwards never retracts a trophy already awarded.
 *
 * <p>{@code userId} being null is meaningful — it records that the month <em>was</em>
 * evaluated and nobody cleared the games threshold. Without that verdict the award job would
 * re-examine the month on every tick forever. Use {@link #hasWinner()} rather than testing
 * the field, so the intent reads at the call site.
 *
 * <p>{@code groupId}/{@code userId} are plain columns rather than {@code @ManyToOne}
 * associations, matching {@code NotificationLog}: rows are written from a scheduled job and
 * nothing navigates from a trophy back to the group or user.
 */
@Entity
@Table(name = "monthly_trophy")
public class MonthlyTrophy extends Auditable {

    @Id
    @GeneratedValue
    @Column(name = "id")
    private UUID id;

    @Column(name = "group_id", nullable = false)
    private UUID groupId;

    @Column(name = "user_id")
    private UUID userId;

    /** First day of the awarded month, in the job's configured zone. */
    @Column(name = "month", nullable = false)
    private LocalDate month;

    @Column(name = "rating")
    private BigDecimal rating;

    @Column(name = "games_played")
    private Integer gamesPlayed;

    @Column(name = "wins")
    private Integer wins;

    protected MonthlyTrophy() {
        // for JPA
    }

    public UUID getId() {
        return id;
    }

    public UUID getGroupId() {
        return groupId;
    }

    public UUID getUserId() {
        return userId;
    }

    public LocalDate getMonth() {
        return month;
    }

    public BigDecimal getRating() {
        return rating;
    }

    public Integer getGamesPlayed() {
        return gamesPlayed;
    }

    public Integer getWins() {
        return wins;
    }

    /** False when the month closed with nobody clearing the games threshold. */
    public boolean hasWinner() {
        return userId != null;
    }
}
