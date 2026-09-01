package com.org.playboard.service.auth;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.org.playboard.common.ApiException;
import com.org.playboard.common.AvatarColorPicker;
import com.org.playboard.common.DefaultAvatars;
import com.org.playboard.common.EmailNormalizer;
import com.org.playboard.dto.auth.TokenResponse;
import com.org.playboard.dto.user.UserSummaryDto;
import com.org.playboard.entity.auth.AuthProvider;
import com.org.playboard.entity.auth.RefreshToken;
import com.org.playboard.entity.auth.UserAuthIdentity;
import com.org.playboard.entity.user.User;
import com.org.playboard.repository.auth.RefreshTokenRepository;
import com.org.playboard.repository.auth.UserAuthIdentityRepository;
import com.org.playboard.repository.user.UserRepository;
import com.org.playboard.service.user.AvatarUrlResolver;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserAuthIdentityRepository identityRepository;
    private final GoogleTokenVerifier googleTokenVerifier;
    private final AppleTokenVerifier appleTokenVerifier;
    private final JwtService jwtService;
    private final AvatarUrlResolver avatarUrls;

    public AuthService(
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            UserAuthIdentityRepository identityRepository,
            GoogleTokenVerifier googleTokenVerifier,
            AppleTokenVerifier appleTokenVerifier,
            JwtService jwtService,
            AvatarUrlResolver avatarUrls) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.identityRepository = identityRepository;
        this.googleTokenVerifier = googleTokenVerifier;
        this.appleTokenVerifier = appleTokenVerifier;
        this.jwtService = jwtService;
        this.avatarUrls = avatarUrls;
    }

    @Transactional
    public TokenResponse signInWithGoogle(String googleIdToken) {
        GoogleIdToken.Payload payload = googleTokenVerifier.verify(googleIdToken);
        String googleSub = payload.getSubject();
        // Normalize so the lookup agrees with any pre-created (add-by-email) row.
        String email = EmailNormalizer.normalize(payload.getEmail());

        User user = resolveUser(AuthProvider.GOOGLE, googleSub, email, (String) payload.get("name"));
        if (user.getGoogleSub() == null) {
            user.setGoogleSub(googleSub);
        }
        return authenticatedResponse(user);
    }

    /** Exchanges a verified native Apple identity credential for a Playboard session. */
    @Transactional
    public TokenResponse signInWithApple(
            String identityToken, String givenName, String familyName) {
        VerifiedIdentity identity = appleTokenVerifier.verify(identityToken, givenName, familyName);
        User user = resolveUser(
                AuthProvider.APPLE,
                identity.subject(),
                identity.email(),
                identity.displayName());
        return authenticatedResponse(user);
    }

    @Transactional
    public TokenResponse refresh(String refreshTokenJwt) {
        JwtService.RefreshTokenClaims claims = jwtService.verifyRefreshToken(refreshTokenJwt);
        if (!userRepository.existsByIdAndDeletedAtIsNull(claims.userId())) {
            throw new ApiException(
                    HttpStatus.UNAUTHORIZED, "REFRESH_TOKEN_INVALID", "Refresh token expired or revoked");
        }
        RefreshToken stored =
                refreshTokenRepository
                        .findByIdAndRevokedAtIsNull(claims.refreshTokenId())
                        .filter(rt -> rt.isActive(Instant.now()))
                        .orElseThrow(() -> new ApiException(
                                HttpStatus.UNAUTHORIZED, "REFRESH_TOKEN_INVALID", "Refresh token expired or revoked"));

        // Rotate: revoke the presented token and issue a fresh pair, so a
        // stolen-and-reused old refresh token is detectable (its row is
        // already revoked by the time an attacker tries it).
        stored.setRevokedAt(Instant.now());

        TokenPair tokens = issueTokenPair(claims.userId());
        return TokenResponse.withoutUser(
                tokens.accessToken(), tokens.refreshToken(), JwtService.ACCESS_TOKEN_TTL.toSeconds());
    }

    @Transactional
    public void logout(String refreshTokenJwt) {
        JwtService.RefreshTokenClaims claims = jwtService.verifyRefreshToken(refreshTokenJwt);
        refreshTokenRepository
                .findByIdAndRevokedAtIsNull(claims.refreshTokenId())
                .ifPresent(rt -> rt.setRevokedAt(Instant.now()));
    }

    private TokenPair issueTokenPair(UUID userId) {
        Instant expiresAt = Instant.now().plus(JwtService.REFRESH_TOKEN_TTL);
        RefreshToken refreshTokenEntity = new RefreshToken();
        refreshTokenEntity.setUser(userRepository.getReferenceById(userId));
        refreshTokenEntity.setExpiresAt(expiresAt);
        refreshTokenEntity = refreshTokenRepository.save(refreshTokenEntity);

        String accessToken = jwtService.issueAccessToken(userId);
        String refreshToken = jwtService.issueRefreshToken(userId, refreshTokenEntity.getId(), expiresAt);
        return new TokenPair(accessToken, refreshToken);
    }

    private User resolveUser(
            AuthProvider provider, String subject, String rawEmail, String displayName) {
        return identityRepository
                .findByProviderAndSubject(provider, subject)
                .map(UserAuthIdentity::getUser)
                .orElseGet(() -> {
                    String email = rawEmail == null ? null : EmailNormalizer.normalize(rawEmail);
                    User user = email == null
                            ? null
                            : userRepository.findByEmail(email).orElse(null);
                    if (user == null) {
                        if (email == null) {
                            throw new ApiException(
                                    HttpStatus.CONFLICT,
                                    "AUTH_EMAIL_REQUIRED",
                                    "The provider did not supply an email for a new Playboard account");
                        }
                        user = createUser(email, displayName);
                    }
                    linkIdentity(user, provider, subject);
                    return user;
                });
    }

    private User createUser(String email, String displayName) {
        User user = new User();
        user.setEmail(email);
        user.setDisplayName(displayName != null ? displayName : email);
        user.setAvatarColor(AvatarColorPicker.pick(email));
        user.setAvatarId(DefaultAvatars.pickRandom());
        return userRepository.save(user);
    }

    private void linkIdentity(User user, AuthProvider provider, String subject) {
        if (identityRepository.existsByUserIdAndProvider(user.getId(), provider)) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "AUTH_PROVIDER_ALREADY_LINKED",
                    "This Playboard account is already linked to another " + provider.apiValue() + " identity");
        }
        UserAuthIdentity identity = new UserAuthIdentity();
        identity.setUser(user);
        identity.setProvider(provider);
        identity.setSubject(subject);
        identityRepository.save(identity);
    }

    private TokenResponse authenticatedResponse(User user) {
        TokenPair tokens = issueTokenPair(user.getId());
        List<String> providers = identityRepository.findAllByUserIdOrderByProvider(user.getId()).stream()
                .map(identity -> identity.getProvider().apiValue())
                .toList();
        return new TokenResponse(
                tokens.accessToken(),
                tokens.refreshToken(),
                JwtService.ACCESS_TOKEN_TTL.toSeconds(),
                UserSummaryDto.from(user, avatarUrls, providers));
    }

    private record TokenPair(String accessToken, String refreshToken) {}
}
