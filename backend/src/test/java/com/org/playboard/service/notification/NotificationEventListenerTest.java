package com.org.playboard.service.notification;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.org.playboard.entity.group.GroupMember;
import com.org.playboard.entity.group.MemberStatus;
import com.org.playboard.entity.user.User;
import com.org.playboard.repository.group.GroupMemberRepository;
import com.org.playboard.service.notification.events.MatchRecordedEvent;
import com.org.playboard.service.notification.events.MemberAddedEvent;
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
        // Build the member mocks first — constructing them inside the thenReturn(...)
        // argument would nest stubbing and trip Mockito's UnfinishedStubbing check.
        GroupMember actorMember = member(actor);
        GroupMember otherMember = member(other);
        when(memberRepo.findByGroupIdAndStatus(groupId, MemberStatus.ACTIVE))
                .thenReturn(List.of(actorMember, otherMember));

        listener.onMatchRecorded(new MatchRecordedEvent(groupId, "Sunday Club", actor, "A beat B", UUID.randomUUID()));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<UUID>> recipients = ArgumentCaptor.forClass(List.class);
        verify(push).sendToUsers(recipients.capture(), eq("New match in Sunday Club"), eq("A beat B"), any());
        // The actor is excluded; only the other member is notified.
        org.junit.jupiter.api.Assertions.assertEquals(List.of(other), recipients.getValue());
    }

    @Test
    void memberAddedNotifiesTheAddedUser() {
        UUID groupId = UUID.randomUUID();
        UUID added = UUID.randomUUID();

        listener.onMemberAdded(new MemberAddedEvent(groupId, "Sunday Club", added));

        verify(push).sendToUsers(eq(List.of(added)), eq("Sunday Club"), any(), any(Map.class));
    }

    private GroupMember member(UUID userId) {
        var user = mock(User.class);
        when(user.getId()).thenReturn(userId);
        var member = mock(GroupMember.class);
        when(member.getUser()).thenReturn(user);
        return member;
    }
}
