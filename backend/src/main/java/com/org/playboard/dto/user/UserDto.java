package com.org.playboard.dto.user;

import com.org.playboard.entity.user.User;
import java.time.Instant;
import java.util.UUID;

public record UserDto(
        UUID id, String displayName, String email, String photoUrl, String avatarColor, Instant createdAt) {

    public static UserDto from(User user) {
        return new UserDto(
                user.getId(),
                user.getDisplayName(),
                user.getEmail(),
                user.getPhotoUrl(),
                user.getAvatarColor(),
                user.getCreatedAt());
    }
}
