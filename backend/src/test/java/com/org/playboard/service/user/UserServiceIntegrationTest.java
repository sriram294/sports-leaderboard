package com.org.playboard.service.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.org.playboard.common.ApiException;
import com.org.playboard.dto.user.UserDto;
import com.org.playboard.entity.user.User;
import com.org.playboard.repository.user.UserRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;

// Live-DB test: exercises GET/PATCH /users/me and the avatar upload/replace
// flow through UserService + AvatarStorageService's real filesystem writes.
@SpringBootTest
@Transactional
class UserServiceIntegrationTest {

    @Autowired private UserService userService;
    @Autowired private UserRepository userRepository;

    @Value("${playboard.storage.avatar-dir}")
    private String avatarDir;

    @Test
    void updatesDisplayName() {
        User user = userRepository.save(newUser());

        UserDto updated = userService.updateDisplayName(user.getId(), "New Name");

        assertThat(updated.displayName()).isEqualTo("New Name");
        assertThat(userService.getById(user.getId()).displayName()).isEqualTo("New Name");
    }

    @Test
    void uploadsAndReplacesAvatarPhoto() throws Exception {
        User user = userRepository.save(newUser());

        MockMultipartFile png = new MockMultipartFile("file", "photo.png", "image/png", new byte[] {1, 2, 3});
        UserDto afterPng = userService.updatePhoto(user.getId(), png);
        assertThat(afterPng.photoUrl()).endsWith("/avatars/" + user.getId() + ".png");
        assertThat(Files.exists(Path.of(avatarDir, user.getId() + ".png"))).isTrue();

        // Re-uploading with a different content type must replace, not accumulate, the stored file.
        MockMultipartFile jpeg = new MockMultipartFile("file", "photo.jpg", "image/jpeg", new byte[] {4, 5, 6});
        UserDto afterJpeg = userService.updatePhoto(user.getId(), jpeg);
        assertThat(afterJpeg.photoUrl()).endsWith("/avatars/" + user.getId() + ".jpg");
        assertThat(Files.exists(Path.of(avatarDir, user.getId() + ".png")))
                .as("stale .png from the first upload should have been removed")
                .isFalse();
    }

    @Test
    void selectingAvatarSetsIdAndClearsPhoto() throws Exception {
        User user = userRepository.save(newUser());
        MockMultipartFile png = new MockMultipartFile("file", "photo.png", "image/png", new byte[] {1, 2, 3});
        userService.updatePhoto(user.getId(), png);
        assertThat(Files.exists(Path.of(avatarDir, user.getId() + ".png"))).isTrue();

        UserDto updated = userService.updateAvatar(user.getId(), "avatar0");

        assertThat(updated.avatarId()).isEqualTo("avatar0");
        assertThat(updated.photoUrl()).isNull();
        assertThat(Files.exists(Path.of(avatarDir, user.getId() + ".png")))
                .as("uploaded photo file should be removed when switching to a default avatar")
                .isFalse();
    }

    @Test
    void uploadingPhotoClearsSelectedAvatar() {
        User user = userRepository.save(newUser());
        userService.updateAvatar(user.getId(), "avatar0");

        MockMultipartFile png = new MockMultipartFile("file", "photo.png", "image/png", new byte[] {1, 2, 3});
        UserDto updated = userService.updatePhoto(user.getId(), png);

        assertThat(updated.avatarId()).isNull();
        assertThat(updated.photoUrl()).isNotNull();
    }

    @Test
    void rejectsUnknownAvatarId() {
        User user = userRepository.save(newUser());

        assertThatThrownBy(() -> userService.updateAvatar(user.getId(), "not-a-real-avatar"))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo("AVATAR_INVALID_ID"));
    }

    @Test
    void rejectsNonImageUpload() {
        User user = userRepository.save(newUser());
        MockMultipartFile file = new MockMultipartFile("file", "notes.txt", "text/plain", new byte[] {1});

        assertThatThrownBy(() -> userService.updatePhoto(user.getId(), file))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo("AVATAR_INVALID_FILE"));
    }

    @Test
    void unknownUserRaisesNotFound() {
        assertThatThrownBy(() -> userService.getById(UUID.randomUUID()))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo("USER_NOT_FOUND"));
    }

    private static User newUser() {
        User user = new User();
        user.setEmail("user-test-" + UUID.randomUUID() + "@example.com");
        user.setDisplayName("User Test");
        user.setAvatarColor("#7ED321");
        return user;
    }
}
