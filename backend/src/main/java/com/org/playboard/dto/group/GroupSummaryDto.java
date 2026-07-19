package com.org.playboard.dto.group;

import com.org.playboard.entity.group.Group;
import com.org.playboard.entity.group.GroupRole;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

/** Backs both the {@code GET /groups} list items and the create/join responses — same shape either way. */
public record GroupSummaryDto(
        UUID id,
        String name,
        String avatarColor,
        String sportCode,
        long memberCount,
        long matchCount,
        String myRole,
        String sessionStart,
        String sessionEnd) {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    public static GroupSummaryDto of(Group group, long memberCount, long matchCount, GroupRole myRole) {
        return new GroupSummaryDto(
                group.getId(),
                group.getName(),
                group.getAvatarColor(),
                group.getSport().getCode(),
                memberCount,
                matchCount,
                myRole.name().toLowerCase(Locale.ROOT),
                formatTime(group.getSessionStart()),
                formatTime(group.getSessionEnd()));
    }

    private static String formatTime(LocalTime time) {
        return time == null ? null : time.format(TIME);
    }
}
