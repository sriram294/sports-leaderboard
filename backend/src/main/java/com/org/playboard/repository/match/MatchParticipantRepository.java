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

    // Backs the windowed leaderboard (This Week / This Month) — aggregates raw
    // matches by played_at for a whole group in one set-based query, mirroring the
    // per-team PF/PA and win logic that StatsRecalculationService.recomputeForPlayer
    // applies per player. count(distinct m.id) keeps GP correct despite the
    // match_sets fan-out. Guests are filtered out by the caller. Window is [from, to).
    @Query(value = """
        select mp.user_id as userId,
               count(distinct m.id) as gamesPlayed,
               count(distinct case when mt.is_winner then m.id end) as wins,
               coalesce(sum(case when mt.team_no = 1 then s.team1_score else s.team2_score end), 0) as pointsFor,
               coalesce(sum(case when mt.team_no = 1 then s.team2_score else s.team1_score end), 0) as pointsAgainst
        from match_participants mp
          join matches m on m.id = mp.match_id
            and m.group_id = :groupId and m.is_deleted = false
            and m.played_at >= :from and m.played_at < :to
          join match_teams mt on mt.id = mp.match_team_id
          left join match_sets s on s.match_id = m.id
        group by mp.user_id
        """, nativeQuery = true)
    List<WindowedStatRow> aggregateWindowedStats(
            @Param("groupId") UUID groupId, @Param("from") Instant from, @Param("to") Instant to);

    interface WindowedStatRow {
        UUID getUserId();

        long getGamesPlayed();

        long getWins();

        long getPointsFor();

        long getPointsAgainst();
    }
}
