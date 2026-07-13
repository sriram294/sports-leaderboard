package com.org.playboard.service.notification;

import com.org.playboard.entity.group.GroupMember;
import com.org.playboard.entity.group.MemberStatus;
import com.org.playboard.repository.group.GroupMemberRepository;
import com.org.playboard.service.notification.events.MatchRecordedEvent;
import com.org.playboard.service.notification.events.MatchUpdatedEvent;
import com.org.playboard.service.notification.events.MemberAddedEvent;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Turns domain events into push notifications. Handlers run {@code AFTER_COMMIT}
 * (so nothing is sent for a rolled-back write) and {@code @Async} (so a slow FCM
 * call never blocks the request thread).
 */
@Component
public class NotificationEventListener {

    private final GroupMemberRepository groupMemberRepository;
    private final PushNotificationService pushNotificationService;

    public NotificationEventListener(
            GroupMemberRepository groupMemberRepository, PushNotificationService pushNotificationService) {
        this.groupMemberRepository = groupMemberRepository;
        this.pushNotificationService = pushNotificationService;
    }

    @Async
    @TransactionalEventListener
    public void onMatchRecorded(MatchRecordedEvent event) {
        pushNotificationService.sendToUsers(
                recipientsExcept(event.groupId(), event.actorUserId()),
                "New match in " + event.groupName(),
                event.summary(),
                Map.of("type", "match", "groupId", event.groupId().toString(), "matchId", event.matchId().toString()));
    }

    @Async
    @TransactionalEventListener
    public void onMatchUpdated(MatchUpdatedEvent event) {
        pushNotificationService.sendToUsers(
                recipientsExcept(event.groupId(), event.actorUserId()),
                "Match updated in " + event.groupName(),
                event.summary(),
                Map.of("type", "match", "groupId", event.groupId().toString(), "matchId", event.matchId().toString()));
    }

    @Async
    @TransactionalEventListener
    public void onMemberAdded(MemberAddedEvent event) {
        pushNotificationService.sendToUsers(
                List.of(event.addedUserId()),
                event.groupName(),
                "You were added to " + event.groupName(),
                Map.of("type", "group", "groupId", event.groupId().toString()));
    }

    /** Active members of the group other than the actor (guests have no tokens, so they self-filter). */
    private List<UUID> recipientsExcept(UUID groupId, UUID actorUserId) {
        return groupMemberRepository.findByGroupIdAndStatus(groupId, MemberStatus.ACTIVE).stream()
                .map(GroupMember::getUser)
                .map(u -> u.getId())
                .filter(id -> !id.equals(actorUserId))
                .toList();
    }
}
