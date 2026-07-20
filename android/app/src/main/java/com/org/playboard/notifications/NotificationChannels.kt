package com.org.playboard.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.content.getSystemService
import com.org.playboard.R

/**
 * Notification channels the app posts to. Channels must exist before any
 * notification is shown (Android 8+), so [ensureCreated] is called once at
 * app startup.
 *
 * One channel per kind of push, so muting the daily recap in system settings
 * doesn't also silence match activity. The backend names the channel on every
 * push (see `NotificationCategory` server-side) — these ids must match it.
 */
object NotificationChannels {

    /** Matches logged or edited. Id unchanged since the single-channel days, so existing installs keep their setting. */
    fun matchActivityId(context: Context): String =
        context.getString(R.string.notification_channel_match_activity)

    /** End-of-session recap, e.g. leaderboard movement. */
    fun dailySummaryId(context: Context): String =
        context.getString(R.string.notification_channel_daily_summary)

    /** Roster and admin changes. */
    fun groupUpdatesId(context: Context): String =
        context.getString(R.string.notification_channel_group_updates)

    /**
     * Channel for a push's `type` data key, used as a fallback when FCM didn't carry
     * an explicit channel id. Unknown types fall back to match activity — the channel
     * every install already has.
     */
    fun forType(context: Context, type: String?): String = when (type) {
        "rank_change" -> dailySummaryId(context)
        "group" -> groupUpdatesId(context)
        else -> matchActivityId(context)
    }

    fun ensureCreated(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService<NotificationManager>() ?: return
        listOf(
            Triple(
                matchActivityId(context),
                R.string.notification_channel_match_activity_name,
                R.string.notification_channel_match_activity_desc,
            ),
            Triple(
                dailySummaryId(context),
                R.string.notification_channel_daily_summary_name,
                R.string.notification_channel_daily_summary_desc,
            ),
            Triple(
                groupUpdatesId(context),
                R.string.notification_channel_group_updates_name,
                R.string.notification_channel_group_updates_desc,
            ),
        ).forEach { (id, nameRes, descRes) ->
            manager.createNotificationChannel(
                NotificationChannel(id, context.getString(nameRes), NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = context.getString(descRes)
                },
            )
        }
    }
}
