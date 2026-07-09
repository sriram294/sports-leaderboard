package com.org.playboard.dto.user;

import jakarta.validation.constraints.NotBlank;

public record UpdateUserRequest(@NotBlank String displayName) {}
