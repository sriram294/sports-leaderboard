package com.org.playboard.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Native Sign in with Apple credential exchanged for a Playboard session. */
public record AppleSignInRequest(
        @NotBlank String identityToken,
        @Size(max = 200) String givenName,
        @Size(max = 200) String familyName) {}
