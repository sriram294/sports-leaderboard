package com.org.playboard.service.auth;

import com.org.playboard.common.ApiException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Issues and verifies Playboard's own access/refresh JWTs, minted once after
 * Google verification — the Google ID token itself is never used as an
 * ongoing API credential (api-contracts.md § Google Sign-In flow).
 */
@Component
public class JwtService {

    public static final Duration ACCESS_TOKEN_TTL = Duration.ofMinutes(15);
    public static final Duration REFRESH_TOKEN_TTL = Duration.ofDays(30);

    private static final String CLAIM_TOKEN_TYPE = "type";
    private static final String TYPE_ACCESS = "access";
    private static final String TYPE_REFRESH = "refresh";

    private final SecretKey signingKey;

    public JwtService(@Value("${playboard.auth.jwt-secret}") String jwtSecret) {
        this.signingKey = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    public String issueAccessToken(UUID userId) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(userId.toString())
                .claim(CLAIM_TOKEN_TYPE, TYPE_ACCESS)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ACCESS_TOKEN_TTL)))
                .signWith(signingKey)
                .compact();
    }

    /** {@code refreshTokenId} is the id of the {@code refresh_tokens} row backing this token (its {@code jti}). */
    public String issueRefreshToken(UUID userId, UUID refreshTokenId, Instant expiresAt) {
        return Jwts.builder()
                .subject(userId.toString())
                .id(refreshTokenId.toString())
                .claim(CLAIM_TOKEN_TYPE, TYPE_REFRESH)
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(expiresAt))
                .signWith(signingKey)
                .compact();
    }

    public UUID verifyAccessToken(String token) {
        try {
            Claims claims = parse(token);
            requireType(claims, TYPE_ACCESS, "ACCESS_TOKEN_INVALID");
            return UUID.fromString(claims.getSubject());
        } catch (JwtException | IllegalArgumentException e) {
            throw unauthorized("ACCESS_TOKEN_INVALID");
        }
    }

    public RefreshTokenClaims verifyRefreshToken(String token) {
        try {
            Claims claims = parse(token);
            requireType(claims, TYPE_REFRESH, "REFRESH_TOKEN_INVALID");
            return new RefreshTokenClaims(
                    UUID.fromString(claims.getSubject()), UUID.fromString(claims.getId()));
        } catch (JwtException | IllegalArgumentException e) {
            throw unauthorized("REFRESH_TOKEN_INVALID");
        }
    }

    private Claims parse(String token) {
        return Jwts.parser().verifyWith(signingKey).build().parseSignedClaims(token).getPayload();
    }

    private void requireType(Claims claims, String expected, String errorCode) {
        if (!expected.equals(claims.get(CLAIM_TOKEN_TYPE, String.class))) {
            throw unauthorized(errorCode);
        }
    }

    private ApiException unauthorized(String code) {
        return new ApiException(HttpStatus.UNAUTHORIZED, code, "Token failed verification");
    }

    public record RefreshTokenClaims(UUID userId, UUID refreshTokenId) {}
}
