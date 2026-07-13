package com.org.playboard.data.device

import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import com.org.playboard.data.remote.PlayboardApi
import com.org.playboard.data.remote.dto.RegisterDeviceRequestDto
import com.org.playboard.data.remote.dto.UnregisterDeviceRequestDto
import com.org.playboard.di.AuthenticatedApi
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Keeps the backend's record of this device's FCM token in sync with the signed-in
 * session. Every call is best-effort: a failure only means this device might miss a
 * push, so errors are logged, never thrown. Registration goes through the
 * [AuthenticatedApi] client so it carries the caller's bearer token.
 */
@Singleton
class DeviceRegistrar @Inject constructor(
    @AuthenticatedApi private val api: PlayboardApi,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Fetch the current FCM token and register it. Call after sign-in. */
    suspend fun register() {
        val token = currentToken() ?: return
        registerToken(token)
    }

    /** Register a specific token (used from [onNewToken] when FCM rotates it). */
    suspend fun registerToken(token: String) {
        runCatching { api.registerDevice(RegisterDeviceRequestDto(token)) }
            .onFailure { Log.w(TAG, "Failed to register device token", it) }
    }

    /**
     * Unregister the current token so a signed-out device stops receiving pushes.
     * Must run while the session is still valid (before tokens are cleared), since
     * it's an authenticated call.
     */
    suspend fun unregister() {
        val token = currentToken() ?: return
        runCatching { api.unregisterDevice(UnregisterDeviceRequestDto(token)) }
            .onFailure { Log.w(TAG, "Failed to unregister device token", it) }
    }

    /** Fire-and-forget registration for non-suspending callers (the messaging service). */
    fun registerTokenAsync(token: String) {
        scope.launch { registerToken(token) }
    }

    private suspend fun currentToken(): String? = suspendCancellableCoroutine { cont ->
        try {
            FirebaseMessaging.getInstance().token
                .addOnSuccessListener { cont.resume(it) }
                .addOnFailureListener {
                    Log.w(TAG, "Could not obtain FCM token", it)
                    cont.resume(null)
                }
        } catch (e: Exception) {
            // FirebaseMessaging is unavailable (e.g. not initialized in a JVM unit
            // test) — degrade to a no-op rather than crashing the caller.
            Log.w(TAG, "FirebaseMessaging unavailable", e)
            cont.resume(null)
        }
    }

    private companion object {
        const val TAG = "DeviceRegistrar"
    }
}
