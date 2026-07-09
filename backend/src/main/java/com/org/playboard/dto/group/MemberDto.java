package com.org.playboard.dto.group;

import com.org.playboard.entity.group.GroupMember;
import java.util.Locale;
import java.util.UUID;

public record MemberDto(UUID userId, String displayName, String photoUrl, String avatarColor, String role) {

    public static MemberDto from(GroupMember member) {
        var user = member.getUser();
        return new MemberDto(
                user.getId(),
                user.getDisplayName(),
                user.getPhotoUrl(),
                user.getAvatarColor(),
                member.getRole().name().toLowerCase(Locale.ROOT));
    }
}
