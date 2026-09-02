package com.org.playboard.entity.match;

import com.org.playboard.common.Auditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/** Durable result of one idempotent match-recording request. */
@Entity
@Table(name = "match_record_requests")
public class MatchRecordRequest extends Auditable {

    @Id
    @GeneratedValue
    @Column(name = "id")
    private UUID id;

    @Column(name = "group_id", nullable = false)
    private UUID groupId;

    @Column(name = "requested_by", nullable = false)
    private UUID requestedBy;

    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Column(name = "match_id")
    private UUID matchId;

    protected MatchRecordRequest() {
        // for JPA
    }

    public MatchRecordRequest(UUID groupId, UUID requestedBy, String idempotencyKey, String requestHash) {
        this.groupId = groupId;
        this.requestedBy = requestedBy;
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public UUID getMatchId() {
        return matchId;
    }

    public void setMatchId(UUID matchId) {
        this.matchId = matchId;
    }
}
