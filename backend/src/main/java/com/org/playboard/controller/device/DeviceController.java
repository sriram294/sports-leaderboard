package com.org.playboard.controller.device;

import com.org.playboard.dto.device.RegisterDeviceRequest;
import com.org.playboard.dto.device.UnregisterDeviceRequest;
import com.org.playboard.service.device.DeviceService;
import jakarta.validation.Valid;
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

    public DeviceController(DeviceService deviceService) {
        this.deviceService = deviceService;
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
}
