package com.org.playboard.service.update;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.org.playboard.common.ApiException;
import org.junit.jupiter.api.Test;

class AppUpdateServiceTest {
    @Test
    void returnsConfiguredMetadata() {
        var result = new AppUpdateService("3", "1.2", "https://github.com/org/repo/releases/download/v1.2/app.apk").getLatest();
        assertEquals(3, result.versionCode());
        assertEquals("1.2", result.versionName());
        assertEquals("https://github.com/org/repo/releases/download/v1.2/app.apk", result.downloadUrl());
        assertEquals(true, result.available());
    }

    @Test
    void returnsUnavailableWhenUnset() {
        var result = new AppUpdateService("", "", "").getLatest();
        assertFalse(result.available());
    }

    @Test
    void rejectsMalformedConfiguration() {
        assertThrows(ApiException.class, () -> new AppUpdateService("not-a-number", "1.2", "https://github.com/app.apk").getLatest());
        assertThrows(ApiException.class, () -> new AppUpdateService("2", "1.1", "http://github.com/app.apk").getLatest());
        assertThrows(ApiException.class, () -> new AppUpdateService("2", "", "https://github.com/app.apk").getLatest());
    }
}
