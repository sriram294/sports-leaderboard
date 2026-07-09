package com.org.playboard.repository.group;

import com.org.playboard.entity.group.GroupMember;
import com.org.playboard.entity.group.MemberStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GroupMemberRepository extends JpaRepository<GroupMember, UUID> {

    Optional<GroupMember> findByGroupIdAndUserId(UUID groupId, UUID userId);

    List<GroupMember> findByGroupIdAndStatus(UUID groupId, MemberStatus status);

    List<GroupMember> findByUserIdAndStatus(UUID userId, MemberStatus status);

    long countByGroupIdAndStatus(UUID groupId, MemberStatus status);
}
