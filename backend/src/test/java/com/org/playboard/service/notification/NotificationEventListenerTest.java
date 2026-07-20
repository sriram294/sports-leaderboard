package com.org.playboard.service.notification;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.org.playboard.entity.group.GroupRole;
import com.org.playboard.entity.group.MemberStatus;
import com.org.playboard.repository.group.GroupMemberRepository;
import com.org.playboard.service.notification.events.MatchRecordedEvent;
import com.org.playboard.service.notification.events.MemberAddedEvent;
import com.org.playboard.service.notification.events.MemberRoleChangedEvent;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class NotificationEventListenerTest {

    private final GroupMemberRepository memberRepo = mock(GroupMemberRepository.class);
    private final PushNotificationService push = mock(PushNotificationService.class);
    private final NotificationEventListener listener = new NotificationEventListener(memberRepo, push);

    @Test
    void matchRecordedNotifiesEveryActiveMemberExceptActor() {
        UUID groupId = UUID.randomUUID();
        UUID actor = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        when(memberRepo.findUserIdsByGroupIdAndStatus(groupId, MemberStatus.ACTIVE))
                .thenReturn(List.of(actor, other));

        listener.onMatchRecorded(new MatchRecordedEvent(groupId, "Sunday Club", actor, "A beat B", UUID.randomUUID()));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<UUID>> recipients = ArgumentCaptor.forClass(List.class);
        verify(push).sendToUsers(
                recipients.capture(),
                eq(NotificationCategory.MATCH_ACTIVITY),
                eq("New match in Sunday Club"),
                eq("A beat B"),
                any());
        // The actor is excluded; only the other member is notified.
        org.junit.jupiter.api.Assertions.assertEquals(List.of(other), recipients.getValue());
    }

    @Test
    void memberAddedNotifiesTheAddedUser() {
        UUID groupId = UUID.randomUUID();
        UUID added = UUID.randomUUID();

        listener.onMemberAdded(new MemberAddedEvent(groupId, "Sunday Club", added));

        verify(push).sendToUsers(
                eq(List.of(added)),
                eq(NotificationCategory.GROUP_UPDATE),
                eq("Sunday Club"),
                any(),
                any(Map.class));
    }

    @Test
    void promotionToAdminNotifiesThePromotedMember() {
        UUID groupId = UUID.randomUUID();
        UUID promoted = UUID.randomUUID();

        listener.onMemberRoleChanged(
                new MemberRoleChangedEvent(groupId, "Sunday Club", promoted, GroupRole.ADMIN));

        verify(push).sendToUsers(
                eq(List.of(promoted)),
                eq(NotificationCategory.GROUP_UPDATE),
                eq("Sunday Club"),
                eq("You're now an admin of Sunday Club"),
                any(Map.class));
    }

    @Test
    void demotionNotifiesNobody() {
        listener.onMemberRoleChanged(
                new MemberRoleChangedEvent(UUID.randomUUID(), "Sunday Club", UUID.randomUUID(), GroupRole.MEMBER));

        verifyNoInteractions(push);
    }
}
