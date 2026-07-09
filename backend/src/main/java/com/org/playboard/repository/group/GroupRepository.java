package com.org.playboard.repository.group;

import com.org.playboard.entity.group.Group;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GroupRepository extends JpaRepository<Group, UUID> {

    @Query("""
        select gm.group from GroupMember gm
        where gm.user.id = :userId and gm.status = com.org.playboard.entity.group.MemberStatus.ACTIVE
        order by gm.group.name
        """)
    List<Group> findActiveGroupsForUser(@Param("userId") UUID userId);
}
