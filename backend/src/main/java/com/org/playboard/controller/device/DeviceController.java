package com.org.playboard.controller.device;

import com.org.playboard.dto.device.RegisterDeviceRequest;
import com.org.playboard.dto.device.UnregisterDeviceRequest;
import com.org.playboard.service.device.DeviceService;
import com.org.playboard.service.notification.NotificationCategory;
import com.org.playboard.service.notification.PushNotificationService;
import com.org.playboard.service.notification.PushResult;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/devices")
public class DeviceController {

    private final DeviceService deviceService;
    private final PushNotificationService pushNotificationService;

    public DeviceController(DeviceService deviceService, PushNotificationService pushNotificationService) {
        this.deviceService = deviceService;
        this.pushNotificationService = pushNotificationService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void register(
            @AuthenticationPrincipal UUID userId, @Valid @RequestBody RegisterDeviceRequest request) {
        deviceService.register(userId, request);
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unregister(
            @AuthenticationPrincipal UUID userId, @Valid @RequestBody UnregisterDeviceRequest request) {
        deviceService.unregister(userId, request.token());
    }

    /**
     * Diagnostic: send a test push to the caller's OWN registered devices and
     * return FCM's accept/reject result. Isolates FCM delivery from the match /
     * event pipeline — if this reports {@code sent > 0} but no notification shows,
     * the problem is on-device; if it reports failures, the error codes say why.
     */
    @PostMapping("/test")
    public PushResult sendTest(@AuthenticationPrincipal UUID userId) {
        return pushNotificationService.sendToUsers(
                List.of(userId),
                NotificationCategory.MATCH_ACTIVITY,
                "Playboard test",
                "If you can see this, push notifications work 🏸",
                Map.of("type", "test"));
    }
}

