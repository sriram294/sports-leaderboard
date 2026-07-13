package com.org.playboard

import android.app.Application
import com.org.playboard.data.auth.ActivityProvider
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class PlayboardApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Track the foreground Activity so Credential Manager (Google Sign-In) can
        // launch its picker with an Activity-based context — see [ActivityProvider].
        registerActivityLifecycleCallbacks(ActivityProvider)
    }
}
