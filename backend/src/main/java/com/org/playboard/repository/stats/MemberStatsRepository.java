package com.org.playboard.repository.stats;

import com.org.playboard.entity.stats.MemberStats;
import com.org.playboard.entity.stats.MemberStatsId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MemberStatsRepository extends JpaRepository<MemberStats, MemberStatsId> {

    /**
     * Canonical leaderboard order: win rate, then points difference (for − against),
     * then wins.
     *
     * <p>Win rate already normalises for games played, so at an equal rate "more wins"
     * only means "played more"; points difference instead ranks on how decisively the
     * matches were won. The trailing user-id key is arbitrary but deterministic — without
     * it, fully tied rows come back in whatever order Postgres happens to yield and can
     * swap places between requests.
     */
    @Query("""
        select ms from MemberStats ms
        where ms.id.groupId = :groupId
        order by ms.winRate desc, (ms.pointsFor - ms.pointsAgainst) desc, ms.wins desc, ms.id.userId asc
        """)
    List<MemberStats> findLeaderboard(@Param("groupId") UUID groupId);

    @Query("""
        select ms from MemberStats ms
        where ms.id.groupId = :groupId and ms.id.userId = :userId
        """)
    Optional<MemberStats> findByGroupIdAndUserId(@Param("groupId") UUID groupId, @Param("userId") UUID userId);
}
