package com.org.playboard.service.group;

import com.org.playboard.common.ApiException;
import com.org.playboard.entity.group.GroupMember;
import com.org.playboard.entity.group.GroupRole;
import com.org.playboard.entity.group.MemberStatus;
import com.org.playboard.repository.group.GroupMemberRepository;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Shared "is caller an active member/role" check for every group-scoped
 * endpoint. Plain method-level checks rather than {@code @PreAuthorize} SpEL
 * — the rule needs a DB lookup keyed on two path/JWT values, which reads
 * more clearly as a normal method call than as a SpEL expression, and stays
 * easy to unit-test directly (see project-structure.md § Open questions,
 * now resolved).
 */
@Component
public class GroupMembershipGuard {

    private final GroupMemberRepository groupMemberRepository;

    public GroupMembershipGuard(GroupMemberRepository groupMemberRepository) {
        this.groupMemberRepository = groupMemberRepository;
    }

    public GroupMember requireActiveMember(UUID groupId, UUID userId) {
        return groupMemberRepository
                .findByGroupIdAndUserId(groupId, userId)
                .filter(member -> member.getStatus() == MemberStatus.ACTIVE)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.FORBIDDEN, "GROUP_ACCESS_FORBIDDEN", "Not an active member of this group"));
    }

    public GroupMember requireRole(UUID groupId, UUID userId, Set<GroupRole> allowedRoles) {
        GroupMember member = requireActiveMember(groupId, userId);
        if (!allowedRoles.contains(member.getRole())) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN, "GROUP_ROLE_FORBIDDEN", "Caller's role does not permit this action");
        }
        return member;
    }
}
