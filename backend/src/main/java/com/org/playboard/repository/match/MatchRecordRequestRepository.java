package com.org.playboard.repository.match;

import com.org.playboard.entity.match.MatchRecordRequest;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MatchRecordRequestRepository extends JpaRepository<MatchRecordRequest, UUID> {

    Optional<MatchRecordRequest> findByGroupIdAndRequestedByAndIdempotencyKey(
            UUID groupId, UUID requestedBy, String idempotencyKey);

    /** Serializes requests for the same caller/group/key, including when no row exists yet. */
    @Query(value = "select pg_advisory_xact_lock(hashtextextended(:lockKey, 0))", nativeQuery = true)
    void lock(@Param("lockKey") String lockKey);
}
