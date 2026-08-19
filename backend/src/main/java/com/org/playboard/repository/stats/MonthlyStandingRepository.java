package com.org.playboard.repository.stats;

import com.org.playboard.entity.stats.MonthlyStanding;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MonthlyStandingRepository extends JpaRepository<MonthlyStanding, UUID> {
    List<MonthlyStanding> findByGroupIdAndMonthOrderByRank(UUID groupId, LocalDate month);

    interface MonthlyFinishRow {
        LocalDate getMonth();
        Integer getRank();
        Integer getQualifiedPlayers();
    }

    /** Latest captured months, including months in which the requested player has no row. */
    @Query(value = """
            select t.month as month,
                   case when s.provisional = false then s.rank else null end as rank,
                   (select count(*)::int from monthly_standing qualified
                     where qualified.group_id = t.group_id and qualified.month = t.month
                       and qualified.provisional = false) as "qualifiedPlayers"
              from monthly_trophy t
              left join monthly_standing s on s.group_id = t.group_id and s.month = t.month
                                           and s.user_id = :userId
             where t.group_id = :groupId and t.standings_captured = true
             order by t.month desc
             limit 12
            """, nativeQuery = true)
    List<MonthlyFinishRow> findLatestFinishes(
            @Param("groupId") UUID groupId, @Param("userId") UUID userId);
}
