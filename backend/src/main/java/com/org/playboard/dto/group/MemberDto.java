package com.org.playboard.dto.group;

import com.org.playboard.entity.group.GroupMember;
import com.org.playboard.service.user.AvatarUrlResolver;
import java.util.Locale;
import java.util.UUID;

public record MemberDto(
        UUID userId, String displayName, String photoUrl, String avatarId, String avatarColor, String role) {

    public static MemberDto from(GroupMember member, AvatarUrlResolver avatarUrls) {
        var user = member.getUser();
        return new MemberDto(
                user.getId(),
                user.getDisplayName(),
                avatarUrls.resolve(user.getPhotoUrl()),
                user.getAvatarId(),
                user.getAvatarColor(),
                member.getRole().name().toLowerCase(Locale.ROOT));
    }
}
