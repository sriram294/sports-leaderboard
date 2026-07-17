package com.org.playboard.dto.user;

import com.org.playboard.entity.user.User;
import java.util.UUID;

public record UserSummaryDto(
        UUID id, String displayName, String email, String photoUrl, String avatarId, String avatarColor) {

    public static UserSummaryDto from(User user) {
        return new UserSummaryDto(
                user.getId(),
                user.getDisplayName(),
                user.getEmail(),
                user.getPhotoUrl(),
                user.getAvatarId(),
                user.getAvatarColor());
    }
}
