package com.org.playboard.service.notification.events;

import java.util.UUID;

/** Published after a user is added to a group by an admin; notifies the added user. */
public record MemberAddedEvent(UUID groupId, String groupName, UUID addedUserId) {}
