package com.org.playboard.service.device;

import com.org.playboard.dto.device.RegisterDeviceRequest;
import com.org.playboard.entity.device.DeviceToken;
import com.org.playboard.repository.device.DeviceTokenRepository;
import com.org.playboard.repository.user.UserRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Manages the FCM registration tokens a user's devices report. Registration is an
 * upsert on the token itself: the same token re-registered by a different user
 * (shared device) is reassigned rather than duplicated (the {@code token} column
 * is unique).
 */
@Service
public class DeviceService {

    private final DeviceTokenRepository deviceTokenRepository;
    private final UserRepository userRepository;

    public DeviceService(DeviceTokenRepository deviceTokenRepository, UserRepository userRepository) {
        this.deviceTokenRepository = deviceTokenRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public void register(UUID userId, RegisterDeviceRequest request) {
        DeviceToken deviceToken = deviceTokenRepository
                .findByToken(request.token())
                .orElseGet(DeviceToken::new);
        deviceToken.setUser(userRepository.getReferenceById(userId));
        deviceToken.setToken(request.token());
        deviceToken.setPlatform(request.platformOrDefault());
        deviceTokenRepository.save(deviceToken);
    }

    /**
     * Unregisters a token. Scoped to the caller so a client can only drop its own
     * device — a token owned by someone else is silently ignored.
     */
    @Transactional
    public void unregister(UUID userId, String token) {
        deviceTokenRepository
                .findByToken(token)
                .filter(dt -> dt.getUser().getId().equals(userId))
                .ifPresent(dt -> deviceTokenRepository.deleteByToken(dt.getToken()));
    }
}
