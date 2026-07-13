package com.org.playboard.service.notification.events;

import java.util.UUID;

/** Published after a match is edited. See {@link MatchRecordedEvent} for why the summary is prebuilt. */
public record MatchUpdatedEvent(
        UUID groupId, String groupName, UUID actorUserId, String summary, UUID matchId) {}
