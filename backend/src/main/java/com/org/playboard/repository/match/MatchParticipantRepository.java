package com.org.playboard.repository.match;

import com.org.playboard.entity.match.Match;
import com.org.playboard.entity.match.MatchParticipant;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MatchParticipantRepository extends JpaRepository<MatchParticipant, UUID> {

    List<MatchParticipant> findByMatchId(UUID matchId);

    List<MatchParticipant> findByMatchTeamId(UUID matchTeamId);

    List<MatchParticipant> findByUserId(UUID userId);

    void deleteByMatchId(UUID matchId);

    // Backs StatsRecalculationService's full rescan (sums + streaks in one
    // pass) for a single player in a single group — see data-model.md
    // § Recompute strategy.
    @Query("""
        select mp.match.id as matchId, m.playedAt as playedAt, mt.teamNo as teamNo, mt.winner as winner
        from MatchParticipant mp
          join mp.match m
          join mp.matchTeam mt
        where mp.user.id = :userId and m.group.id = :groupId and m.deleted = false
        order by m.playedAt asc, m.id asc
        """)
    List<PlayerMatchRow> findPlayerMatchHistory(@Param("groupId") UUID groupId, @Param("userId") UUID userId);

    interface PlayerMatchRow {
        UUID getMatchId();

        Instant getPlayedAt();

        short getTeamNo();

        boolean isWinner();
    }

    // Backs PlayerStatsDto.recentMatches — newest first, caller applies the cap via Pageable.
    @Query("""
        select mp.match from MatchParticipant mp
        where mp.user.id = :userId and mp.match.group.id = :groupId and mp.match.deleted = false
        order by mp.match.playedAt desc, mp.match.id desc
        """)
    List<Match> findRecentMatchesForPlayer(@Param("groupId") UUID groupId, @Param("userId") UUID userId, Pageable pageable);

    // Backs "Best Partner" (computed on demand, not materialized — see
    // data-model.md § Recompute strategy). Ad-hoc join on the shared
    // match_team_id since there's no mapped inverse association from
    // MatchTeam back to its participants.
    @Query("""
        select mp2.user.id as partnerId, mp.matchTeam.winner as winner
        from MatchParticipant mp
          join MatchParticipant mp2 on mp2.matchTeam = mp.matchTeam
        where mp.user.id = :userId and mp2.user.id <> :userId
          and mp.match.group.id = :groupId and mp.match.deleted = false
        """)
    List<PartnerRow> findPartnerHistory(@Param("groupId") UUID groupId, @Param("userId") UUID userId);

    interface PartnerRow {
        UUID getPartnerId();

        boolean isWinner();
    }
}
