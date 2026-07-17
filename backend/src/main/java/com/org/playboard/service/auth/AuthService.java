package com.org.playboard.service.auth;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.org.playboard.common.ApiException;
import com.org.playboard.common.AvatarColorPicker;
import com.org.playboard.common.DefaultAvatars;
import com.org.playboard.common.EmailNormalizer;
import com.org.playboard.dto.auth.TokenResponse;
import com.org.playboard.dto.user.UserSummaryDto;
import com.org.playboard.entity.auth.RefreshToken;
import com.org.playboard.entity.user.User;
import com.org.playboard.repository.auth.RefreshTokenRepository;
import com.org.playboard.repository.user.UserRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final GoogleTokenVerifier googleTokenVerifier;
    private final JwtService jwtService;

    public AuthService(
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            GoogleTokenVerifier googleTokenVerifier,
            JwtService jwtService) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.googleTokenVerifier = googleTokenVerifier;
        this.jwtService = jwtService;
    }

    @Transactional
    public TokenResponse signInWithGoogle(String googleIdToken) {
        GoogleIdToken.Payload payload = googleTokenVerifier.verify(googleIdToken);
        String googleSub = payload.getSubject();
        // Normalize so the lookup agrees with any pre-created (add-by-email) row.
        String email = EmailNormalizer.normalize(payload.getEmail());

        User user =
                userRepository
                        .findByGoogleSub(googleSub)
                        .or(() -> userRepository.findByEmail(email).map(existing -> linkGoogleSub(existing, googleSub)))
                        .orElseGet(() -> createUser(googleSub, email, (String) payload.get("name")));

        TokenPair tokens = issueTokenPair(user.getId());
        return new TokenResponse(
                tokens.accessToken(),
                tokens.refreshToken(),
                JwtService.ACCESS_TOKEN_TTL.toSeconds(),
                UserSummaryDto.from(user));
    }

    @Transactional
    public TokenResponse refresh(String refreshTokenJwt) {
        JwtService.RefreshTokenClaims claims = jwtService.verifyRefreshToken(refreshTokenJwt);
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

    private User linkGoogleSub(User user, String googleSub) {
        user.setGoogleSub(googleSub);
        return user;
    }

    private User createUser(String googleSub, String email, String displayName) {
        User user = new User();
        user.setGoogleSub(googleSub);
        user.setEmail(email);
        user.setDisplayName(displayName != null ? displayName : email);
        user.setAvatarColor(AvatarColorPicker.pick(email));
        user.setAvatarId(DefaultAvatars.pickRandom());
        return userRepository.save(user);
    }

    private record TokenPair(String accessToken, String refreshToken) {}
}
