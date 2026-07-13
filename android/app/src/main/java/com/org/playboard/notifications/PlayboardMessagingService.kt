package com.org.playboard.notifications

import android.Manifest
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.org.playboard.MainActivity
import com.org.playboard.R
import com.org.playboard.data.auth.TokenStore
import com.org.playboard.data.device.DeviceRegistrar
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.runBlocking

/**
 * Receives FCM callbacks. Not a Hilt-injected component (Firebase constructs it),
 * so collaborators are pulled from an [EntryPoint].
 *
 * The backend sends a `notification` payload, so when the app is backgrounded the
 * system tray displays the push itself (on the channel named in the manifest).
 * This class only needs to (a) re-register a rotated token and (b) render the
 * notification when the app is in the foreground.
 */
class PlayboardMessagingService : FirebaseMessagingService() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface MessagingEntryPoint {
        fun deviceRegistrar(): DeviceRegistrar
        fun tokenStore(): TokenStore
    }

    private val entryPoint: MessagingEntryPoint by lazy {
        EntryPointAccessors.fromApplication(applicationContext, MessagingEntryPoint::class.java)
    }

    override fun onNewToken(token: String) {
        // Only meaningful while signed in — an unauthenticated register would 401.
        val signedIn = runBlocking { entryPoint.tokenStore().currentAccessToken() != null }
        if (signedIn) {
            entryPoint.deviceRegistrar().registerTokenAsync(token)
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val notification = message.notification
        val title = notification?.title ?: message.data["title"] ?: getString(R.string.app_name)
        val body = notification?.body ?: message.data["body"] ?: return
        showNotification(title, body)
    }

    private fun showNotification(title: String, body: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val builder = NotificationCompat.Builder(this, NotificationChannels.matchActivityId(this))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        NotificationManagerCompat.from(this)
            .notify((System.currentTimeMillis() % Int.MAX_VALUE).toInt(), builder.build())
    }
}
