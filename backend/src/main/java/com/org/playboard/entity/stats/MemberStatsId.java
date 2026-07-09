package com.org.playboard.entity.stats;

import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class MemberStatsId implements Serializable {

    private UUID groupId;
    private UUID userId;

    public MemberStatsId() {
    }

    public MemberStatsId(UUID groupId, UUID userId) {
        this.groupId = groupId;
        this.userId = userId;
    }

    public UUID getGroupId() {
        return groupId;
    }

    public void setGroupId(UUID groupId) {
        this.groupId = groupId;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof MemberStatsId that)) {
            return false;
        }
        return Objects.equals(groupId, that.groupId) && Objects.equals(userId, that.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(groupId, userId);
    }
}
