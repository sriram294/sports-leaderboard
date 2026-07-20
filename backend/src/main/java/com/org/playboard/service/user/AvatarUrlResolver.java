package com.org.playboard.service.user;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Expands the relative avatar paths held in {@code users.photo_url} into absolute
 * URLs for API responses.
 *
 * <p>Avatar paths are stored host-free (e.g. {@code /avatars/<id>.jpg}) so that
 * moving the API to a different domain needs no data migration — the host is
 * applied here, at read time, from {@code PUBLIC_BASE_URL}. Storing the absolute
 * URL instead (as this originally did) froze the old host into every row and
 * broke every existing photo the moment that domain went away.
 */
@Component
public class AvatarUrlResolver {

    private final String publicBaseUrl;

    public AvatarUrlResolver(@Value("${playboard.storage.public-base-url}") String publicBaseUrl) {
        // A trailing slash would double up against the leading slash of the stored path.
        this.publicBaseUrl = publicBaseUrl.endsWith("/")
                ? publicBaseUrl.substring(0, publicBaseUrl.length() - 1)
                : publicBaseUrl;
    }

    /**
     * Returns an absolute, client-loadable URL for a stored avatar path, or null
     * when the user has no uploaded photo.
     *
     * <p>Values that are already absolute are passed through untouched, so rows
     * written before the V10 migration keep working.
     */
    public String resolve(String storedPath) {
        if (storedPath == null || storedPath.isBlank()) {
            return null;
        }
        if (storedPath.startsWith("/")) {
            return publicBaseUrl + storedPath;
        }
        return storedPath;
    }
}
