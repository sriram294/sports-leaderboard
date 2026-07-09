package com.org.playboard.dto.group;

import jakarta.validation.constraints.NotBlank;

public record JoinGroupRequest(@NotBlank String code) {}
