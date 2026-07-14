package com.org.playboard.controller.update;

import com.org.playboard.dto.update.AppUpdateResponse;
import com.org.playboard.service.update.AppUpdateService;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/app")
public class AppUpdateController {
    private final AppUpdateService appUpdateService;

    public AppUpdateController(AppUpdateService appUpdateService) {
        this.appUpdateService = appUpdateService;
    }

    @SecurityRequirements
    @GetMapping("/update")
    public AppUpdateResponse getUpdate() {
        return appUpdateService.getLatest();
    }
}
