package com.org.playboard.service.user;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

class AvatarStorageServiceTest {

    @TempDir
    Path avatarDir;

    @Test
    void replacementUsesNewUrlAndRemovesPreviousFile() {
        AvatarStorageService storage = new AvatarStorageService(avatarDir.toString());
        UUID userId = UUID.randomUUID();

        String firstPath = storage.store(
                userId, new MockMultipartFile("file", "first.png", "image/png", new byte[] {1, 2, 3}));
        String secondPath = storage.store(
                userId, new MockMultipartFile("file", "second.png", "image/png", new byte[] {4, 5, 6}));

        assertThat(secondPath)
                .startsWith("/avatars/" + userId + "-")
                .endsWith(".png")
                .isNotEqualTo(firstPath);
        assertThat(Files.exists(avatarDir.resolve(filename(firstPath)))).isFalse();
        assertThat(Files.exists(avatarDir.resolve(filename(secondPath)))).isTrue();
    }

    @Test
    void removeDeletesVersionedPhoto() {
        AvatarStorageService storage = new AvatarStorageService(avatarDir.toString());
        UUID userId = UUID.randomUUID();
        String storedPath = storage.store(
                userId, new MockMultipartFile("file", "photo.jpg", "image/jpeg", new byte[] {1, 2, 3}));

        storage.remove(userId);

        assertThat(Files.exists(avatarDir.resolve(filename(storedPath)))).isFalse();
    }

    private static String filename(String path) {
        return path.substring(path.lastIndexOf('/') + 1);
    }
}
