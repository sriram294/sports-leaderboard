package com.org.playboard.dto.update;

/** Public metadata for the latest sideloaded debug APK. */
public record AppUpdateResponse(
        Integer versionCode,
        String versionName,
        String downloadUrl,
        boolean available) {
    public static AppUpdateResponse unavailable() {
        return new AppUpdateResponse(null, null, null, false);
    }
}
