package com.org.playboard.dto.user;

import com.org.playboard.entity.user.User;
import com.org.playboard.service.user.AvatarUrlResolver;
import java.time.Instant;
import java.util.UUID;

public record UserDto(
        UUID id,
        String displayName,
        String email,
        String photoUrl,
        String avatarId,
        String avatarColor,
        Instant createdAt) {

    public static UserDto from(User user, AvatarUrlResolver avatarUrls) {
        return new UserDto(
                user.getId(),
                user.getDisplayName(),
                user.getEmail(),
                avatarUrls.resolve(user.getPhotoUrl()),
                user.getAvatarId(),
                user.getAvatarColor(),
                user.getCreatedAt());
    }
}
