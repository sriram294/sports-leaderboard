package com.org.playboard.common;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Catalog of the bundled default avatars. Each id is the basename of a PNG
 * shipped inside the Android app under {@code assets/avatars/<id>.png}, so the
 * backend only persists the lightweight id (see {@link
 * com.org.playboard.entity.user.User#getAvatarId}) — never the image bytes. A
 * new user is assigned a random one at creation; guests are intentionally left
 * without an avatar so they keep their muted gray initial.
 *
 * <p>Keep this list in lockstep with the files under the app's
 * {@code assets/avatars/} directory.
 */
public final class DefaultAvatars {

    public static final List<String> IDS = List.of(
            "avatar0", "avatar1", "avatar2", "avatar3", "avatar4", "avatar5",
            "avatar6", "avatar7", "avatar8", "avatar9", "avatar10", "avatar11",
            "avatar12", "avatar13", "avatar14", "avatar15");

    private DefaultAvatars() {}

    /** A random avatar id, assigned to a member on creation. */
    public static String pickRandom() {
        return IDS.get(ThreadLocalRandom.current().nextInt(IDS.size()));
    }

    public static boolean isValid(String avatarId) {
        return avatarId != null && IDS.contains(avatarId);
    }
}
