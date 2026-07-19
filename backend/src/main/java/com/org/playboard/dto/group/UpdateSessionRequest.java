package com.org.playboard.dto.group;

/**
 * Body for {@code PATCH /groups/{groupId}/session} — the group's daily playing window as
 * "HH:mm" strings. Both null clears the window; both set must have {@code start < end}.
 */
public record UpdateSessionRequest(String start, String end) {}
