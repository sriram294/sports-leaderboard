package com.org.playboard.repository.match;

import com.org.playboard.entity.match.MatchEvent;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MatchEventRepository extends JpaRepository<MatchEvent, UUID> {

    List<MatchEvent> findByMatchIdOrderByCreatedAt(UUID matchId);
}
