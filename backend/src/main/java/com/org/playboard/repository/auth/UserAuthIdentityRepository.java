package com.org.playboard.repository.auth;

import com.org.playboard.entity.auth.AuthProvider;
import com.org.playboard.entity.auth.UserAuthIdentity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAuthIdentityRepository extends JpaRepository<UserAuthIdentity, UUID> {

    Optional<UserAuthIdentity> findByProviderAndSubject(AuthProvider provider, String subject);

    boolean existsByUserIdAndProvider(UUID userId, AuthProvider provider);

    List<UserAuthIdentity> findAllByUserIdOrderByProvider(UUID userId);
}
