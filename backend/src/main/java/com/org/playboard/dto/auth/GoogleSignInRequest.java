package com.org.playboard.dto.auth;

import jakarta.validation.constraints.NotBlank;

public record GoogleSignInRequest(@NotBlank String idToken) {}
