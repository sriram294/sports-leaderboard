package com.org.playboard.service.notification;

import com.org.playboard.entity.group.MemberStatus;
import com.org.playboard.repository.group.GroupMemberRepository;
import com.org.playboard.service.notification.events.MatchRecordedEvent;
import com.org.playboard.service.notification.events.MatchUpdatedEvent;
import com.org.playboard.service.notification.events.MemberAddedEvent;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(NotificationEventListener.class);

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
        List<UUID> recipients = recipientsExcept(event.groupId(), event.actorUserId());
        log.info("MatchRecorded in group {}: notifying {} member(s).", event.groupId(), recipients.size());
        pushNotificationService.sendToUsers(
                recipients,
                "New match in " + event.groupName(),
                event.summary(),
                Map.of("type", "match", "groupId", event.groupId().toString(), "matchId", event.matchId().toString()));
    }

    @Async
    @TransactionalEventListener
    public void onMatchUpdated(MatchUpdatedEvent event) {
        List<UUID> recipients = recipientsExcept(event.groupId(), event.actorUserId());
        log.info("MatchUpdated in group {}: notifying {} member(s).", event.groupId(), recipients.size());
        pushNotificationService.sendToUsers(
                recipients,
                "Match updated in " + event.groupName(),
                event.summary(),
                Map.of("type", "match", "groupId", event.groupId().toString(), "matchId", event.matchId().toString()));
    }

    @Async
    @TransactionalEventListener
    public void onMemberAdded(MemberAddedEvent event) {
        log.info("MemberAdded to group {}: notifying the added user.", event.groupId());
        pushNotificationService.sendToUsers(
                List.of(event.addedUserId()),
                event.groupName(),
                "You were added to " + event.groupName(),
                Map.of("type", "group", "groupId", event.groupId().toString()));
    }

    /**
     * Active members of the group other than the actor. Uses an id-projection
     * query (not entity loading) because this runs on a post-commit async thread
     * with no open session — touching a lazy association here would throw. Guests
     * self-filter (they have no device tokens).
     */
    private List<UUID> recipientsExcept(UUID groupId, UUID actorUserId) {
        return groupMemberRepository.findUserIdsByGroupIdAndStatus(groupId, MemberStatus.ACTIVE).stream()
                .filter(id -> !id.equals(actorUserId))
                .toList();
    }
}
