package com.org.playboard.repository.match;

import com.org.playboard.entity.match.MatchTeam;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MatchTeamRepository extends JpaRepository<MatchTeam, UUID> {

    List<MatchTeam> findByMatchIdOrderByTeamNo(UUID matchId);

    void deleteByMatchId(UUID matchId);
}
