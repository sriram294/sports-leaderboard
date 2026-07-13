package com.org.playboard.data.auth

import android.app.Activity
import android.app.Application
import android.os.Bundle
import java.lang.ref.WeakReference

/**
 * Tracks the currently-resumed [Activity] so platform APIs that require an
 * **Activity-based** context can reach one without a ViewModel or app-scoped
 * singleton holding a `Context` (which would leak / break unit tests).
 *
 * Credential Manager's account-picker UI is the motivating case: calling
 * `getCredential` with the *application* context throws
 * `GetCredentialUnknownException: Failed to launch the selector UI. Hint: ensure
 * the 'context' parameter is an Activity-based context` on stricter OEM builds.
 *
 * Registered app-wide in [com.org.playboard.PlayboardApplication.onCreate]. The
 * reference is weak and cleared on destroy, so it never keeps a finished Activity
 * alive.
 */
object ActivityProvider : Application.ActivityLifecycleCallbacks {

    private var activityRef: WeakReference<Activity> = WeakReference(null)

    /** The foreground Activity, or `null` if none is currently resumed. */
    val currentActivity: Activity?
        get() = activityRef.get()

    override fun onActivityResumed(activity: Activity) {
        activityRef = WeakReference(activity)
    }

    override fun onActivityDestroyed(activity: Activity) {
        if (activityRef.get() === activity) {
            activityRef = WeakReference(null)
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityStarted(activity: Activity) {}
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivityStopped(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
}
