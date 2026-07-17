package com.org.playboard.service.user;

import com.org.playboard.common.ApiException;
import com.org.playboard.common.DefaultAvatars;
import com.org.playboard.dto.user.UserDto;
import com.org.playboard.entity.user.User;
import com.org.playboard.repository.user.UserRepository;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final AvatarStorageService avatarStorageService;

    public UserService(UserRepository userRepository, AvatarStorageService avatarStorageService) {
        this.userRepository = userRepository;
        this.avatarStorageService = avatarStorageService;
    }

    @Transactional(readOnly = true)
    public UserDto getById(UUID userId) {
        return UserDto.from(findUser(userId));
    }

    @Transactional
    public UserDto updateDisplayName(UUID userId, String displayName) {
        User user = findUser(userId);
        user.setDisplayName(displayName);
        return UserDto.from(user);
    }

    @Transactional
    public UserDto updatePhoto(UUID userId, MultipartFile file) {
        User user = findUser(userId);
        user.setPhotoUrl(avatarStorageService.store(userId, file));
        // A photo and a default avatar are mutually exclusive — the photo wins.
        user.setAvatarId(null);
        return UserDto.from(user);
    }

    @Transactional
    public UserDto updateAvatar(UUID userId, String avatarId) {
        if (!DefaultAvatars.isValid(avatarId)) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "AVATAR_INVALID_ID", "Unknown avatar id");
        }
        User user = findUser(userId);
        user.setAvatarId(avatarId);
        // Picking a default avatar replaces any uploaded photo — drop the file too.
        if (user.getPhotoUrl() != null) {
            avatarStorageService.remove(userId);
            user.setPhotoUrl(null);
        }
        return UserDto.from(user);
    }

    private User findUser(UUID userId) {
        return userRepository
                .findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found"));
    }
}
