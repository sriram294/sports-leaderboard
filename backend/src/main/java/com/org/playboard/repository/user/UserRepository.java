package com.org.playboard.repository.user;

import com.org.playboard.entity.user.User;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByGoogleSub(String googleSub);

    Optional<User> findByEmail(String email);
}
