package com.org.playboard.repository.auth;

import com.org.playboard.entity.auth.RefreshToken;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByIdAndRevokedAtIsNull(UUID id);
}
