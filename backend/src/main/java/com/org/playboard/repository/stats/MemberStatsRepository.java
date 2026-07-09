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

    @Query("""
        select ms from MemberStats ms
        where ms.id.groupId = :groupId
        order by ms.winRate desc, ms.wins desc
        """)
    List<MemberStats> findLeaderboard(@Param("groupId") UUID groupId);

    @Query("""
        select ms from MemberStats ms
        where ms.id.groupId = :groupId and ms.id.userId = :userId
        """)
    Optional<MemberStats> findByGroupIdAndUserId(@Param("groupId") UUID groupId, @Param("userId") UUID userId);
}
