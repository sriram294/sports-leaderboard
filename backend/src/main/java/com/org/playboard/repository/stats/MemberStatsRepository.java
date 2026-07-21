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
     * Every member's stats row for a group, unordered.
     *
     * <p>Deliberately has no {@code order by}: ranking is
     * {@link com.org.playboard.service.stats.LeaderboardRanker}'s job, because the
     * provisional threshold depends on the group's median games played — a fact about other
     * rows that SQL can't express here — so the result would be re-sorted in Java anyway.
     * Ordering here would be dead code that could only drift from the real comparator.
     */
    @Query("""
        select ms from MemberStats ms
        where ms.id.groupId = :groupId
        """)
    List<MemberStats> findByGroupId(@Param("groupId") UUID groupId);

    @Query("""
        select ms from MemberStats ms
        where ms.id.groupId = :groupId and ms.id.userId = :userId
        """)
    Optional<MemberStats> findByGroupIdAndUserId(@Param("groupId") UUID groupId, @Param("userId") UUID userId);
}
