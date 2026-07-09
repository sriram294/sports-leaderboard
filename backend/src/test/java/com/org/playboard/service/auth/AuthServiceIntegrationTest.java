package com.org.playboard.service.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.org.playboard.common.ApiException;
import com.org.playboard.dto.auth.TokenResponse;
import com.org.playboard.entity.auth.RefreshToken;
import com.org.playboard.entity.user.User;
import com.org.playboard.repository.auth.RefreshTokenRepository;
import com.org.playboard.repository.user.UserRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

// Exercises JwtService + AuthService's refresh/logout rotation against a live
// Postgres, bypassing GoogleTokenVerifier (can't obtain a real Google ID
// token in a test) by seeding a refresh_tokens row directly, the same way
// AuthService.signInWithGoogle would have.
@SpringBootTest
@Transactional
class AuthServiceIntegrationTest {

    @Autowired private AuthService authService;
    @Autowired private JwtService jwtService;
    @Autowired private RefreshTokenRepository refreshTokenRepository;
    @Autowired private UserRepository userRepository;

    @Test
    void accessTokenRoundTrips() {
        User user = userRepository.save(newUser());
        String accessToken = jwtService.issueAccessToken(user.getId());

        assertThat(jwtService.verifyAccessToken(accessToken)).isEqualTo(user.getId());
    }

    @Test
    void refreshRotatesTokenAndLogoutRevokesIt() {
        User user = userRepository.save(newUser());
        String firstRefreshJwt = seedRefreshToken(user);

        TokenResponse rotated = authService.refresh(firstRefreshJwt);
        assertThat(rotated.accessToken()).isNotBlank();
        assertThat(rotated.refreshToken()).isNotBlank();
        assertThat(rotated.user()).isNull();

        // Original token is now revoked — reusing it must fail, not rotate again.
        assertThatThrownBy(() -> authService.refresh(firstRefreshJwt))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo("REFRESH_TOKEN_INVALID"));

        // The rotated token works exactly once, then logout revokes it too.
        authService.logout(rotated.refreshToken());
        assertThatThrownBy(() -> authService.refresh(rotated.refreshToken()))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo("REFRESH_TOKEN_INVALID"));
    }

    @Test
    void expiredRefreshTokenIsRejected() {
        User user = userRepository.save(newUser());

        RefreshToken expired = new RefreshToken();
        expired.setUser(user);
        expired.setExpiresAt(Instant.now().minusSeconds(60));
        expired = refreshTokenRepository.save(expired);

        String expiredJwt = jwtService.issueRefreshToken(user.getId(), expired.getId(), expired.getExpiresAt());

        assertThatThrownBy(() -> authService.refresh(expiredJwt))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo("REFRESH_TOKEN_INVALID"));
    }

    private String seedRefreshToken(User user) {
        Instant expiresAt = Instant.now().plusSeconds(3600);
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUser(user);
        refreshToken.setExpiresAt(expiresAt);
        refreshToken = refreshTokenRepository.save(refreshToken);
        return jwtService.issueRefreshToken(user.getId(), refreshToken.getId(), expiresAt);
    }

    private static User newUser() {
        User user = new User();
        user.setEmail("auth-test-" + UUID.randomUUID() + "@example.com");
        user.setDisplayName("Auth Test User");
        user.setAvatarColor("#7ED321");
        return user;
    }
}
