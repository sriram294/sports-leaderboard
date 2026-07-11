package com.org.playboard.dto.group;

import jakarta.validation.constraints.NotBlank;

/** Body for {@code PATCH /groups/{groupId}} — rename a group. */
public record RenameGroupRequest(@NotBlank String name) {}
