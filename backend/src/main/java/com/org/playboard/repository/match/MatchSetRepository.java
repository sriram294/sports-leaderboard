package com.org.playboard.repository.match;

import com.org.playboard.entity.match.MatchSet;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MatchSetRepository extends JpaRepository<MatchSet, UUID> {

    List<MatchSet> findByMatchIdOrderBySetNo(UUID matchId);

    void deleteByMatchId(UUID matchId);

    @Query("""
        select ms.match.id as matchId, sum(ms.team1Score) as team1Total, sum(ms.team2Score) as team2Total
        from MatchSet ms
        where ms.match.id in :matchIds
        group by ms.match.id
        """)
    List<MatchScoreTotals> sumScoresByMatchIds(@Param("matchIds") Collection<UUID> matchIds);

    interface MatchScoreTotals {
        UUID getMatchId();

        long getTeam1Total();

        long getTeam2Total();
    }
}
