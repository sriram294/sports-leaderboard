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
 */
object NotificationChannels {

    /** The single channel for group-activity pushes. Value mirrors the string resource. */
    fun matchActivityId(context: Context): String =
        context.getString(R.string.notification_channel_match_activity)

    fun ensureCreated(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService<NotificationManager>() ?: return
        val channel = NotificationChannel(
            matchActivityId(context),
            context.getString(R.string.notification_channel_match_activity_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.notification_channel_match_activity_desc)
        }
        manager.createNotificationChannel(channel)
    }
}
