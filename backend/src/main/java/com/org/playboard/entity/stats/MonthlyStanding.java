package com.org.playboard.entity.stats;

import com.org.playboard.common.Auditable;
import com.org.playboard.dto.stats.LeaderboardEntryDto;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** One immutable player row from a completed month's leaderboard. */
@Entity
@Table(name = "monthly_standing", uniqueConstraints = @UniqueConstraint(
        name = "uq_monthly_standing_group_month_user", columnNames = {"group_id", "month", "user_id"}))
public class MonthlyStanding extends Auditable {
    @Id @GeneratedValue private UUID id;
    @Column(name = "group_id", nullable = false) private UUID groupId;
    @Column(name = "user_id", nullable = false) private UUID userId;
    @Column(nullable = false) private LocalDate month;
    @Column(nullable = false) private int rank;
    @Column(nullable = false) private BigDecimal rating;
    @Column(name = "games_played", nullable = false) private int gamesPlayed;
    @Column(nullable = false) private int wins;
    @Column(nullable = false) private boolean provisional;

    protected MonthlyStanding() {}

    public MonthlyStanding(UUID groupId, LocalDate month, LeaderboardEntryDto entry) {
        this.groupId = groupId;
        this.userId = entry.userId();
        this.month = month;
        this.rank = entry.rank();
        this.rating = entry.rating();
        this.gamesPlayed = entry.gamesPlayed();
        this.wins = entry.wins();
        this.provisional = entry.provisional();
    }

    public UUID getId() { return id; }
    public UUID getGroupId() { return groupId; }
    public UUID getUserId() { return userId; }
    public LocalDate getMonth() { return month; }
    public int getRank() { return rank; }
    public BigDecimal getRating() { return rating; }
    public int getGamesPlayed() { return gamesPlayed; }
    public int getWins() { return wins; }
    public boolean isProvisional() { return provisional; }
}
