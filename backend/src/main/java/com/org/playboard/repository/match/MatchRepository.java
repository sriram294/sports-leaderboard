package com.org.playboard.repository.match;

import com.org.playboard.entity.match.Match;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MatchRepository extends JpaRepository<Match, UUID> {

    Optional<Match> findByIdAndGroupIdAndDeletedFalse(UUID id, UUID groupId);

    long countByGroupIdAndDeletedFalse(UUID groupId);

    // Keyset pagination on the (played_at desc, id desc) index — Pageable here
    // only bounds the row count (page 0), never used for an OFFSET.
    @Query("""
        select m from Match m
        where m.group.id = :groupId and m.deleted = false
        order by m.playedAt desc, m.id desc
        """)
    List<Match> findFirstPage(@Param("groupId") UUID groupId, Pageable pageable);

    @Query("""
        select m from Match m
        where m.group.id = :groupId and m.deleted = false
          and (m.playedAt < :playedAt or (m.playedAt = :playedAt and m.id < :id))
        order by m.playedAt desc, m.id desc
        """)
    List<Match> findNextPage(
            @Param("groupId") UUID groupId,
            @Param("playedAt") Instant playedAt,
            @Param("id") UUID id,
            Pageable pageable);

    // "My matches" filter variants of the two pages above: joined to match_participants so
    // only matches the given user played in are returned. unique(match_id, user_id) means a
    // user appears at most once per match, so the join yields no duplicate match rows.
    @Query("""
        select m from Match m
          join MatchParticipant mp on mp.match = m
        where m.group.id = :groupId and m.deleted = false and mp.user.id = :userId
        order by m.playedAt desc, m.id desc
        """)
    List<Match> findFirstPageForUser(
            @Param("groupId") UUID groupId, @Param("userId") UUID userId, Pageable pageable);

    @Query("""
        select m from Match m
          join MatchParticipant mp on mp.match = m
        where m.group.id = :groupId and m.deleted = false and mp.user.id = :userId
          and (m.playedAt < :playedAt or (m.playedAt = :playedAt and m.id < :id))
        order by m.playedAt desc, m.id desc
        """)
    List<Match> findNextPageForUser(
            @Param("groupId") UUID groupId,
            @Param("userId") UUID userId,
            @Param("playedAt") Instant playedAt,
            @Param("id") UUID id,
            Pageable pageable);
}
