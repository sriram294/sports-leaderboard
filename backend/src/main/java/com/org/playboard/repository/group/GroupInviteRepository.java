package com.org.playboard.repository.group;

import com.org.playboard.entity.group.GroupInvite;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GroupInviteRepository extends JpaRepository<GroupInvite, UUID> {

    Optional<GroupInvite> findByCode(String code);
}
