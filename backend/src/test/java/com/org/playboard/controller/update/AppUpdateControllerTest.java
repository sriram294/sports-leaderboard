package com.org.playboard.controller.update;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.org.playboard.dto.update.AppUpdateResponse;
import com.org.playboard.service.update.AppUpdateService;
import org.junit.jupiter.api.Test;

class AppUpdateControllerTest {
    @Test
    void exposesTheServiceResponseWithoutAuthenticationConcerns() {
        var controller = new AppUpdateController(
                new AppUpdateService("2", "1.1", "https://github.com/org/repo/releases/download/v1.1/app.apk"));
        AppUpdateResponse result = controller.getUpdate();
        assertEquals(2, result.versionCode());
        assertEquals("1.1", result.versionName());
        assertEquals(true, result.available());
    }
}
