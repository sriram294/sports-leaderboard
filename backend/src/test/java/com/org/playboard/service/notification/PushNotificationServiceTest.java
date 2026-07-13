package com.org.playboard.service.notification;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.MulticastMessage;
import com.google.firebase.messaging.SendResponse;
import com.org.playboard.entity.device.DeviceToken;
import com.org.playboard.entity.user.User;
import com.org.playboard.repository.device.DeviceTokenRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class PushNotificationServiceTest {

    private final DeviceTokenRepository repo = mock(DeviceTokenRepository.class);

    @Test
    void noOpWhenFirebaseDisabled() {
        var service = new PushNotificationService(Optional.empty(), repo);

        var result = service.sendToUsers(List.of(UUID.randomUUID()), "title", "body", Map.of());

        org.junit.jupiter.api.Assertions.assertFalse(result.firebaseEnabled());
        // Never even looks up tokens when Firebase isn't configured.
        verify(repo, never()).findByUserIdIn(anyCollection());
    }

    @Test
    void noOpWhenNoTokens() throws Exception {
        var messaging = mock(FirebaseMessaging.class);
        var service = new PushNotificationService(Optional.of(messaging), repo);
        when(repo.findByUserIdIn(anyCollection())).thenReturn(List.of());

        service.sendToUsers(List.of(UUID.randomUUID()), "title", "body", Map.of());

        verify(messaging, never()).sendEachForMulticast(any());
    }

    @Test
    void prunesUnregisteredTokens() throws Exception {
        var messaging = mock(FirebaseMessaging.class);
        var service = new PushNotificationService(Optional.of(messaging), repo);
        when(repo.findByUserIdIn(anyCollection()))
                .thenReturn(List.of(deviceToken("good"), deviceToken("dead")));

        var goodResponse = mock(SendResponse.class);
        when(goodResponse.isSuccessful()).thenReturn(true);
        var deadResponse = mock(SendResponse.class);
        when(deadResponse.isSuccessful()).thenReturn(false);
        var deadException = mock(FirebaseMessagingException.class);
        when(deadException.getMessagingErrorCode()).thenReturn(MessagingErrorCode.UNREGISTERED);
        when(deadResponse.getException()).thenReturn(deadException);

        var batch = mock(BatchResponse.class);
        when(batch.getResponses()).thenReturn(List.of(goodResponse, deadResponse));
        when(messaging.sendEachForMulticast(any(MulticastMessage.class))).thenReturn(batch);

        service.sendToUsers(List.of(UUID.randomUUID()), "title", "body", Map.of());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
        verify(repo).deleteByTokenIn(captor.capture());
        // Only the token FCM reported as UNREGISTERED is pruned — not the healthy one.
        org.junit.jupiter.api.Assertions.assertEquals(List.of("dead"), captor.getValue());
    }

    private DeviceToken deviceToken(String token) {
        var dt = new DeviceToken();
        dt.setUser(new User());
        dt.setToken(token);
        return dt;
    }
}
