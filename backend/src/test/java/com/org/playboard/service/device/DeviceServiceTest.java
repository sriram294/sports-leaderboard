package com.org.playboard.service.device;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.org.playboard.dto.device.RegisterDeviceRequest;
import com.org.playboard.entity.device.DeviceToken;
import com.org.playboard.entity.user.User;
import com.org.playboard.repository.device.DeviceTokenRepository;
import com.org.playboard.repository.user.UserRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class DeviceServiceTest {

    private final DeviceTokenRepository deviceRepo = mock(DeviceTokenRepository.class);
    private final UserRepository userRepo = mock(UserRepository.class);
    private final DeviceService service = new DeviceService(deviceRepo, userRepo);

    @Test
    void registerInsertsNewToken() {
        UUID userId = UUID.randomUUID();
        when(deviceRepo.findByToken("tok")).thenReturn(Optional.empty());
        when(userRepo.getReferenceById(userId)).thenReturn(new User());

        service.register(userId, new RegisterDeviceRequest("tok", "android"));

        ArgumentCaptor<DeviceToken> saved = ArgumentCaptor.forClass(DeviceToken.class);
        verify(deviceRepo).save(saved.capture());
        org.junit.jupiter.api.Assertions.assertEquals("tok", saved.getValue().getToken());
        org.junit.jupiter.api.Assertions.assertEquals("android", saved.getValue().getPlatform());
    }

    @Test
    void registerExistingTokenReassignsUserInsteadOfDuplicating() {
        UUID newUser = UUID.randomUUID();
        var existing = new DeviceToken();
        existing.setUser(new User());
        existing.setToken("tok");
        when(deviceRepo.findByToken("tok")).thenReturn(Optional.of(existing));
        var reassigned = new User();
        when(userRepo.getReferenceById(newUser)).thenReturn(reassigned);

        service.register(newUser, new RegisterDeviceRequest("tok", null));

        // Same row is saved (upsert), reassigned to the new user, platform defaulted.
        verify(deviceRepo).save(existing);
        org.junit.jupiter.api.Assertions.assertSame(reassigned, existing.getUser());
        org.junit.jupiter.api.Assertions.assertEquals("android", existing.getPlatform());
    }

    @Test
    void unregisterOnlyDeletesCallersOwnToken() {
        UUID caller = UUID.randomUUID();
        var otherUser = mock(User.class);
        when(otherUser.getId()).thenReturn(UUID.randomUUID());
        var someoneElsesToken = new DeviceToken();
        someoneElsesToken.setUser(otherUser);
        someoneElsesToken.setToken("tok");
        when(deviceRepo.findByToken("tok")).thenReturn(Optional.of(someoneElsesToken));

        service.unregister(caller, "tok");

        verify(deviceRepo, never()).deleteByToken(any());
    }
}
