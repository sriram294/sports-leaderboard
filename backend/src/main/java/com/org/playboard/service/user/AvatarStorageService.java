package com.org.playboard.service.user;

import com.org.playboard.common.ApiException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * Local-disk avatar storage (dev/small-scale). Swapping to an S3-compatible
 * bucket later is a drop-in replacement of this one class — {@link
 * com.org.playboard.service.user.UserService} only depends on {@link #store}
 * returning a path, not on how/where the bytes land (see project-structure.md
 * § Open questions).
 */
@Component
public class AvatarStorageService {

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/png", "image/jpeg", "image/webp");

    private final Path avatarDir;

    public AvatarStorageService(@Value("${playboard.storage.avatar-dir}") String avatarDir) {
        this.avatarDir = Path.of(avatarDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.avatarDir);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not create avatar storage directory: " + this.avatarDir, e);
        }
    }

    public String store(UUID userId, MultipartFile file) {
        if (file.isEmpty() || !ALLOWED_CONTENT_TYPES.contains(file.getContentType())) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY, "AVATAR_INVALID_FILE", "Photo must be a PNG, JPEG, or WebP image");
        }

        // A new public path on every upload is intentional. Image loaders and
        // intermediary HTTP caches key by URL, so overwriting <userId>.png left
        // other users seeing the previous bytes even after their API data was
        // refreshed. The random suffix makes replacement photos immediately
        // distinguishable without disabling useful avatar caching globally.
        String filename = userId + "-" + UUID.randomUUID() + extensionFor(file.getContentType());
        Path destination = avatarDir.resolve(filename);
        try {
            file.transferTo(destination);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not store avatar for user " + userId, e);
        }
        // Only remove the previous image after the new one was written, so a
        // failed upload cannot erase the user's current photo.
        removeExisting(userId, destination);
        // Host-free on purpose — AvatarUrlResolver applies PUBLIC_BASE_URL at read
        // time. "/avatars/**" is the URL path WebConfig maps onto this directory.
        return "/avatars/" + filename;
    }

    /** Delete any stored photo for the user — called when they switch to a default avatar. */
    public void remove(UUID userId) {
        removeExisting(userId, null);
    }

    // Match both legacy <userId>.<ext> files and versioned
    // <userId>-<uploadId>.<ext> files. A prior upload may also have used a
    // different extension, so clean all variants except the file just written.
    private void removeExisting(UUID userId, Path fileToKeep) {
        String legacyPrefix = userId + ".";
        String versionedPrefix = userId + "-";
        try (var files = Files.list(avatarDir)) {
            files.filter(path -> {
                        String name = path.getFileName().toString();
                        return (name.startsWith(legacyPrefix) || name.startsWith(versionedPrefix))
                                && !path.equals(fileToKeep);
                    })
                    .forEach(this::deleteQuietly);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not list avatar storage directory: " + avatarDir, e);
        }
    }

    private void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // best-effort cleanup; a leftover stale file is harmless since it's never referenced
        }
    }

    private static String extensionFor(String contentType) {
        return switch (contentType) {
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> ".jpg";
        };
    }
}
