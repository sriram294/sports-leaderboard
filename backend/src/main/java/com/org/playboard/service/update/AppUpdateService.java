package com.org.playboard.service.update;

import com.org.playboard.common.ApiException;
import com.org.playboard.dto.update.AppUpdateResponse;
import java.net.URI;
import java.net.URISyntaxException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** Reads and validates the release metadata exposed to debug clients. */
@Service
public class AppUpdateService {
    private final ReleaseMetadata android;
    private final ReleaseMetadata ios;

    public AppUpdateService(
            @Value("${playboard.update.debug.version-code:}") String versionCode,
            @Value("${playboard.update.debug.version-name:}") String versionName,
            @Value("${playboard.update.debug.download-url:}") String downloadUrl) {
        this(versionCode, versionName, downloadUrl, "", "", "");
    }

    public AppUpdateResponse getLatest() {
        return getLatest("android");
    }

    public AppUpdateResponse getLatest(String platform) {
        ReleaseMetadata metadata = switch (platform == null ? "" : platform.trim().toLowerCase()) {
            case "android" -> android;
            case "ios" -> ios;
            default -> throw new ApiException(HttpStatus.BAD_REQUEST, "APP_UPDATE_PLATFORM_UNSUPPORTED", "Supported platforms are android and ios");
        };
        return metadata.response();
    }

    private static final class ReleaseMetadata {
        private final String versionCode;
        private final String versionName;
        private final String downloadUrl;

        private ReleaseMetadata(String versionCode, String versionName, String downloadUrl) {
            this.versionCode = versionCode;
            this.versionName = versionName;
            this.downloadUrl = downloadUrl;
        }

        private AppUpdateResponse response() {
            if (blank(versionCode) && blank(versionName) && blank(downloadUrl)) {
                return AppUpdateResponse.unavailable();
            }
            try {
                int parsedVersion = Integer.parseInt(versionCode);
                if (parsedVersion < 1 || blank(versionName) || blank(downloadUrl)) {
                    throw invalidConfiguration();
                }
                URI uri = new URI(downloadUrl);
                if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
                    throw invalidConfiguration();
                }
                return new AppUpdateResponse(parsedVersion, versionName, downloadUrl, true);
            } catch (NumberFormatException | URISyntaxException ex) {
                throw invalidConfiguration();
            }
        }
    }

    @Autowired
    public AppUpdateService(
            @Value("${playboard.update.debug.version-code:}") String androidVersionCode,
            @Value("${playboard.update.debug.version-name:}") String androidVersionName,
            @Value("${playboard.update.debug.download-url:}") String androidDownloadUrl,
            @Value("${playboard.update.debug.ios.version-code:}") String iosVersionCode,
            @Value("${playboard.update.debug.ios.version-name:}") String iosVersionName,
            @Value("${playboard.update.debug.ios.download-url:}") String iosDownloadUrl) {
        android = new ReleaseMetadata(androidVersionCode, androidVersionName, androidDownloadUrl);
        ios = new ReleaseMetadata(iosVersionCode, iosVersionName, iosDownloadUrl);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static ApiException invalidConfiguration() {
        return new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "APP_UPDATE_CONFIGURATION_INVALID", "Debug update metadata is invalid");
    }
}
