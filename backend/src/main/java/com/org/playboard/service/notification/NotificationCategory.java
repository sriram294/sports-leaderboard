package com.org.playboard.service.notification;

/**
 * The kinds of push this app sends. Each maps 1:1 to an Android notification
 * channel, so a user can mute one kind in system settings without silencing the
 * rest.
 *
 * <p>The channel id travels with every push (see
 * {@link PushNotificationService#sendToUsers}). Without it FCM renders a
 * backgrounded push on the manifest's {@code default_notification_channel_id},
 * which would put every category back on one channel and undo the separation.
 *
 * <p>Ids must match the channel strings in
 * {@code android/app/src/main/res/values/strings.xml}.
 */
public enum NotificationCategory {

    /** A match was logged or edited. Pre-dates this enum; id kept so existing installs keep their setting. */
    MATCH_ACTIVITY("match_activity"),

    /** End-of-session recap, e.g. a leaderboard position change. */
    DAILY_SUMMARY("daily_summary"),

    /** Roster and admin changes. */
    GROUP_UPDATE("group_updates");

    private final String channelId;

    NotificationCategory(String channelId) {
        this.channelId = channelId;
    }

    public String channelId() {
        return channelId;
    }
}
