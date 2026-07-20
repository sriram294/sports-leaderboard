package com.org.playboard.service.notification.events;

import com.org.playboard.entity.group.GroupRole;
import java.util.UUID;

/**
 * A member's role in a group was changed by the owner. Carries the new role so
 * listeners can distinguish a promotion from a demotion.
 */
public record MemberRoleChangedEvent(UUID groupId, String groupName, UUID targetUserId, GroupRole newRole) {}
