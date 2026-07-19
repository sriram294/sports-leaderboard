package com.org.playboard.dto.group;

import jakarta.validation.constraints.NotBlank;

/** Body for {@code PATCH /groups/{groupId}/members/{userId}} — the new role ("admin" or "member"). */
public record UpdateRoleRequest(@NotBlank String role) {}
