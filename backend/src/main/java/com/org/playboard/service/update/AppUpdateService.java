package com.org.playboard.service.update;

import com.org.playboard.common.ApiException;
import com.org.playboard.dto.update.AppUpdateResponse;
import java.net.URI;
import java.net.URISyntaxException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** Reads and validates the release metadata exposed to debug clients. */
@Service
public class AppUpdateService {
    private final String versionCode;
    private final String versionName;
    private final String downloadUrl;

    public AppUpdateService(
            @Value("${playboard.update.debug.version-code:}") String versionCode,
            @Value("${playboard.update.debug.version-name:}") String versionName,
            @Value("${playboard.update.debug.download-url:}") String downloadUrl) {
        this.versionCode = versionCode;
        this.versionName = versionName;
        this.downloadUrl = downloadUrl;
    }

    public AppUpdateResponse getLatest() {
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

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static ApiException invalidConfiguration() {
        return new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "APP_UPDATE_CONFIGURATION_INVALID", "Debug update metadata is invalid");
    }
}
