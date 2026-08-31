package com.org.playboard.service.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.org.playboard.common.ApiException;
import com.org.playboard.dto.auth.TokenResponse;
import com.org.playboard.entity.auth.AuthProvider;
import com.org.playboard.entity.auth.RefreshToken;
import com.org.playboard.entity.user.User;
import com.org.playboard.repository.auth.RefreshTokenRepository;
import com.org.playboard.repository.auth.UserAuthIdentityRepository;
import com.org.playboard.repository.user.UserRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
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
    @Autowired private UserAuthIdentityRepository identityRepository;
    @Autowired private UserRepository userRepository;

    // Can't obtain a real Google ID token in a test — stub the verifier so we
    // control the (email, sub, name) the sign-in resolves against.
    @MockitoBean private GoogleTokenVerifier googleTokenVerifier;
    @MockitoBean private AppleTokenVerifier appleTokenVerifier;

    @Test
    void signInClaimsPreCreatedEmailUserAndLinksGoogleAccount() {
        // Pre-created by an owner "add member by email": email set, no google_sub.
        User provisional = new User();
        provisional.setEmail("iphone.user@gmail.com");
        provisional.setDisplayName("iPhone User");
        provisional.setAvatarColor("#7ED321");
        provisional = userRepository.save(provisional);
        UUID provisionalId = provisional.getId();
        assertThat(provisional.getGoogleSub()).isNull();

        // Google returns the same email (different casing) + a subject.
        GoogleIdToken.Payload payload = new GoogleIdToken.Payload();
        payload.setSubject("google-sub-123");
        payload.setEmail("IPhone.User@Gmail.com");
        payload.set("name", "Name From Google");
        when(googleTokenVerifier.verify("tok")).thenReturn(payload);

        TokenResponse response = authService.signInWithGoogle("tok");

        // Linked to the SAME row — not a duplicate — with google_sub now set.
        assertThat(response.user().id()).isEqualTo(provisionalId);
        User linked = userRepository.findById(provisionalId).orElseThrow();
        assertThat(linked.getGoogleSub()).isEqualTo("google-sub-123");
        assertThat(linked.getEmail()).isEqualTo("iphone.user@gmail.com"); // unchanged, normalized
        assertThat(userRepository.findByEmail("iphone.user@gmail.com")).isPresent();
        assertThat(response.user().authProviders()).containsExactly("google");
        assertThat(identityRepository.findByProviderAndSubject(AuthProvider.GOOGLE, "google-sub-123"))
                .get()
                .extracting(identity -> identity.getUser().getId())
                .isEqualTo(provisionalId);
    }

    @Test
    void appleLinksTheExistingEmailAccountWithoutBreakingGoogleLogin() {
        GoogleIdToken.Payload googlePayload = new GoogleIdToken.Payload();
        googlePayload.setSubject("google-dual-provider");
        googlePayload.setEmail("dual@example.com");
        googlePayload.set("name", "Dual Provider");
        when(googleTokenVerifier.verify("google-token")).thenReturn(googlePayload);
        TokenResponse google = authService.signInWithGoogle("google-token");

        when(appleTokenVerifier.verify("apple-token", "Dual", "Provider"))
                .thenReturn(new VerifiedIdentity("apple-dual-provider", "DUAL@example.com", "Dual Provider"));
        TokenResponse apple = authService.signInWithApple("apple-token", "Dual", "Provider");

        assertThat(apple.user().id()).isEqualTo(google.user().id());
        assertThat(apple.user().authProviders()).containsExactly("apple", "google");
        assertThat(userRepository.findAll().stream()
                        .filter(user -> "dual@example.com".equals(user.getEmail())))
                .hasSize(1);
    }

    @Test
    void returningAppleUserCanSignInWhenAppleNoLongerSharesEmail() {
        when(appleTokenVerifier.verify("first-token", "First", "Login"))
                .thenReturn(new VerifiedIdentity("apple-returning", "returning@privaterelay.appleid.com", "First Login"));
        TokenResponse first = authService.signInWithApple("first-token", "First", "Login");

        when(appleTokenVerifier.verify("later-token", null, null))
                .thenReturn(new VerifiedIdentity("apple-returning", null, null));
        TokenResponse returning = authService.signInWithApple("later-token", null, null);

        assertThat(returning.user().id()).isEqualTo(first.user().id());
        assertThat(returning.user().displayName()).isEqualTo("First Login");
        assertThat(returning.user().authProviders()).containsExactly("apple");
    }

    @Test
    void newAppleUserWithoutAnEmailFailsDeterministically() {
        when(appleTokenVerifier.verify("no-email", null, null))
                .thenReturn(new VerifiedIdentity("apple-new-no-email", null, null));

        assertThatThrownBy(() -> authService.signInWithApple("no-email", null, null))
                .isInstanceOf(ApiException.class)
                .satisfies(error -> assertThat(((ApiException) error).getCode()).isEqualTo("AUTH_EMAIL_REQUIRED"));
    }

    @Test
    void aSecondAppleSubjectCannotReplaceTheAccountsExistingAppleIdentity() {
        when(appleTokenVerifier.verify("first-apple", "First", "Identity"))
                .thenReturn(new VerifiedIdentity("apple-subject-one", "unique-apple@example.com", "First Identity"));
        authService.signInWithApple("first-apple", "First", "Identity");

        when(appleTokenVerifier.verify("second-apple", null, null))
                .thenReturn(new VerifiedIdentity("apple-subject-two", "unique-apple@example.com", null));

        assertThatThrownBy(() -> authService.signInWithApple("second-apple", null, null))
                .isInstanceOf(ApiException.class)
                .satisfies(error -> assertThat(((ApiException) error).getCode())
                        .isEqualTo("AUTH_PROVIDER_ALREADY_LINKED"));
    }

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
