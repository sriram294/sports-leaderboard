package com.org.playboard.common;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Catalog of the bundled default avatars. Each id is the basename of an SVG
 * shipped inside the Android app under {@code assets/avatars/<id>.svg}, so the
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
            "12ac343930", "1d2f2b4717", "30cf50cc99", "39c95f7d8f", "436068898d",
            "46cb006030", "5f42c81abb", "618be3e104", "7841b5f8c9", "80ad28b394",
            "863d9dc474", "8ecc918224", "8fcadf2717", "91cfff00a6", "b0fceea6c5",
            "b1b3146fcf", "bb357d3d46", "c0b772274a", "cec8d2bf95", "d288110b4b",
            "da87cb62a1", "e7142554e7", "e9c1d9762b", "fb7ecc7bf0", "ff268b8670");

    private DefaultAvatars() {}

    /** A random avatar id, assigned to a member on creation. */
    public static String pickRandom() {
        return IDS.get(ThreadLocalRandom.current().nextInt(IDS.size()));
    }

    public static boolean isValid(String avatarId) {
        return avatarId != null && IDS.contains(avatarId);
    }
}
