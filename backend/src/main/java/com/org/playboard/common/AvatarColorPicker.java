package com.org.playboard.common;

import java.util.List;

/**
 * Fallback palette for colored-initial avatars — used for both user profile
 * photos and group icons, so a given seed (email, group name) always maps
 * to the same stable color without a round trip to the client to pick one.
 */
public final class AvatarColorPicker {

    private static final List<String> COLORS =
            List.of("#7ED321", "#FF3D8A", "#3DB4FF", "#9ADE28", "#F5A623", "#BD10E0");

    private AvatarColorPicker() {}

    public static String pick(String seed) {
        return COLORS.get(Math.floorMod(seed.hashCode(), COLORS.size()));
    }
}
