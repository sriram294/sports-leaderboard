package com.org.playboard.entity.group;

public enum GroupRole {
    OWNER,
    ADMIN,
    MEMBER,
    // A per-group filler identity for one-off non-member players. Excluded from
    // member counts, the leaderboard, and all stats; only selectable when
    // building a match. See V4__group_guests.sql.
    GUEST
}
