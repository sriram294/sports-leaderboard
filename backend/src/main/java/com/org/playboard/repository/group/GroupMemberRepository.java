package com.org.playboard.repository.group;

import com.org.playboard.entity.group.GroupMember;
import com.org.playboard.entity.group.GroupRole;
import com.org.playboard.entity.group.MemberStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GroupMemberRepository extends JpaRepository<GroupMember, UUID> {

    Optional<GroupMember> findByGroupIdAndUserId(UUID groupId, UUID userId);

    List<GroupMember> findByGroupIdAndStatus(UUID groupId, MemberStatus status);

    /**
     * Just the member user ids — used by push-notification recipient resolution,
     * which runs on a post-commit async thread with no open session, so it must
     * avoid touching lazy {@code GroupMember.user} associations.
     */
    @Query("select gm.user.id from GroupMember gm where gm.group.id = :groupId and gm.status = :status")
    List<UUID> findUserIdsByGroupIdAndStatus(@Param("groupId") UUID groupId, @Param("status") MemberStatus status);

    List<GroupMember> findByUserIdAndStatus(UUID userId, MemberStatus status);

    long countByGroupIdAndStatus(UUID groupId, MemberStatus status);

    // Guests (role 'guest') aren't real players, so they never count toward a
    // group's member count.
    long countByGroupIdAndStatusAndRoleNot(UUID groupId, MemberStatus status, GroupRole role);

    List<GroupMember> findByGroupIdAndStatusAndRole(UUID groupId, MemberStatus status, GroupRole role);

    /**
     * Every membership row for a group, whatever its status.
     *
     * <p>Deliberately status-agnostic, unlike its neighbours: it resolves the identities
     * behind historical records (monthly trophies), and a player who has since left the
     * group still won the month they won. Filtering to ACTIVE here would make past winners
     * vanish from the roll of honour the moment they leave.
     */
    List<GroupMember> findByGroupId(UUID groupId);
}
