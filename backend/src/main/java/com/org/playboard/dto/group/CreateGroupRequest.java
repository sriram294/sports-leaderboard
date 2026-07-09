package com.org.playboard.dto.group;

import jakarta.validation.constraints.NotBlank;

public record CreateGroupRequest(@NotBlank String name, @NotBlank String sportCode) {}
