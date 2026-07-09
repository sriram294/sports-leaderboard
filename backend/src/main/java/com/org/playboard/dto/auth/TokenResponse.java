package com.org.playboard.dto.auth;

import com.org.playboard.dto.user.UserSummaryDto;

public record TokenResponse(
        String accessToken, String refreshToken, long expiresIn, UserSummaryDto user) {

    /** {@code POST /auth/refresh} omits {@code user} — same shape minus that field. */
    public static TokenResponse withoutUser(String accessToken, String refreshToken, long expiresIn) {
        return new TokenResponse(accessToken, refreshToken, expiresIn, null);
    }
}
