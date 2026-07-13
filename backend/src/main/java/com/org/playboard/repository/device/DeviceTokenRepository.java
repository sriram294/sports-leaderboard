package com.org.playboard.repository.device;

import com.org.playboard.entity.device.DeviceToken;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface DeviceTokenRepository extends JpaRepository<DeviceToken, UUID> {

    Optional<DeviceToken> findByToken(String token);

    List<DeviceToken> findByUserIdIn(Collection<UUID> userIds);

    List<DeviceToken> findByUserId(UUID userId);

    void deleteByToken(String token);

    @Modifying
    @Query("delete from DeviceToken d where d.token in :tokens")
    void deleteByTokenIn(Collection<String> tokens);
}
