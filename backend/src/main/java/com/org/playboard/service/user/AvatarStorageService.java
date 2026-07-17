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
 * returning a URL, not on how/where the bytes land (see project-structure.md
 * § Open questions).
 */
@Component
public class AvatarStorageService {

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/png", "image/jpeg", "image/webp");

    private final Path avatarDir;
    private final String publicBaseUrl;

    public AvatarStorageService(
            @Value("${playboard.storage.avatar-dir}") String avatarDir,
            @Value("${playboard.storage.public-base-url}") String publicBaseUrl) {
        this.avatarDir = Path.of(avatarDir).toAbsolutePath().normalize();
        this.publicBaseUrl = publicBaseUrl;
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

        removeExisting(userId);
        String filename = userId + extensionFor(file.getContentType());
        try {
            file.transferTo(avatarDir.resolve(filename));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not store avatar for user " + userId, e);
        }
        return publicBaseUrl + "/avatars/" + filename;
    }

    /** Delete any stored photo for the user — called when they switch to a default avatar. */
    public void remove(UUID userId) {
        removeExisting(userId);
    }

    // A prior upload may have used a different extension (e.g. jpg -> png) —
    // clear it so the new upload doesn't leave two files with only one referenced.
    private void removeExisting(UUID userId) {
        String prefix = userId + ".";
        try (var files = Files.list(avatarDir)) {
            files.filter(p -> p.getFileName().toString().startsWith(prefix)).forEach(this::deleteQuietly);
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
