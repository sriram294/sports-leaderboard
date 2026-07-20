package com.org.playboard.dto.user;

import com.org.playboard.entity.user.User;
import com.org.playboard.service.user.AvatarUrlResolver;
import java.util.UUID;

public record UserSummaryDto(
        UUID id, String displayName, String email, String photoUrl, String avatarId, String avatarColor) {

    public static UserSummaryDto from(User user, AvatarUrlResolver avatarUrls) {
        return new UserSummaryDto(
                user.getId(),
                user.getDisplayName(),
                user.getEmail(),
                avatarUrls.resolve(user.getPhotoUrl()),
                user.getAvatarId(),
                user.getAvatarColor());
    }
}
