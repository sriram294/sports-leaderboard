package com.org.playboard.dto.group;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Body for {@code POST /groups/{groupId}/members} — an owner/admin adds a person
 * to the group by email + name, before that person has installed/signed into the
 * app. The row is claimed automatically when they later sign in with Google using
 * the same email (see {@link com.org.playboard.service.auth.AuthService}).
 */
public record AddMemberRequest(@Email @NotBlank String email, @NotBlank String displayName) {}
