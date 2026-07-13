package com.org.playboard.service.notification.events;

import java.util.UUID;

/**
 * Published after a match is created. Carries a pre-built {@code summary} (e.g.
 * "Alice &amp; Bob beat Carol &amp; Dave") because the roster is only conveniently
 * loadable inside the writing transaction — the listener fires after commit.
 */
public record MatchRecordedEvent(
        UUID groupId, String groupName, UUID actorUserId, String summary, UUID matchId) {}
