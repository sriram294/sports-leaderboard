package com.org.playboard

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.svg.SvgDecoder
import com.org.playboard.data.auth.ActivityProvider
import com.org.playboard.notifications.NotificationChannels
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class PlayboardApplication : Application(), SingletonImageLoader.Factory {
    override fun onCreate() {
        super.onCreate()
        // Track the foreground Activity so Credential Manager (Google Sign-In) can
        // launch its picker with an Activity-based context — see [ActivityProvider].
        registerActivityLifecycleCallbacks(ActivityProvider)
        // Notification channels must exist before the first push is shown.
        NotificationChannels.ensureCreated(this)
    }

    // Register the SVG decoder so the bundled default avatars (assets/avatars/*.svg)
    // render through Coil everywhere PlayerAvatar is used.
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(SvgDecoder.Factory()) }
            .build()
}
