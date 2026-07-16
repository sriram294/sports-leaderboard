package com.org.playboard.data.auth

import android.util.Log
import com.org.playboard.data.device.DeviceRegistrar
import com.org.playboard.data.model.SessionState
import com.org.playboard.data.model.UserSession
import com.org.playboard.data.remote.PlayboardApi
import com.org.playboard.data.remote.dto.GoogleSignInRequestDto
import com.org.playboard.data.remote.dto.UserSummaryDto
import com.org.playboard.di.AuthApi
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import retrofit2.HttpException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Singleton
class AuthRepository @Inject constructor(
    @AuthApi private val authApi: PlayboardApi,
    private val tokenStore: TokenStore,
    private val deviceRegistrar: DeviceRegistrar,
) {
    // Tied to this singleton's lifetime (the process), same as an
    // application-scoped coroutine scope would be — kept local rather than
    // adding a dedicated DI-provided scope for a single consumer.
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private companion object {
        const val TAG = "PlayboardAuth"
    }

    val sessionState: StateFlow<SessionState> =
        tokenStore.sessionState.stateIn(repositoryScope, SharingStarted.Eagerly, SessionState.Loading)

    suspend fun signInWithGoogle(idToken: String): Result<Unit> = runCatching {
        Log.i(TAG, "Exchanging Google id token with backend (tokenLen=${idToken.length})")
        val response = authApi.signInWithGoogle(GoogleSignInRequestDto(idToken))
        val user = requireNotNull(response.user) { "Sign-in response missing user" }
        tokenStore.save(response.accessToken, response.refreshToken, user.toUserSession())
        Log.i(TAG, "Backend sign-in succeeded for userId=${user.id}")
        // Best-effort, off the sign-in path: the session is now valid, so the
        // authenticated register call will carry a bearer token.
        repositoryScope.launch { deviceRegistrar.register() }
        Unit
    }.onFailure { logSignInFailure(it) }

    /**
     * Turns a swallowed backend/network failure into a diagnosable Logcat entry.
     * The most useful field cases: an [HttpException] (backend rejected the token,
     * e.g. GOOGLE_CLIENT_ID mismatch → 401) whose status + error body pinpoint the
     * cause, and an [IOException] (no connectivity / Railway cold-start timeout).
     */
    private fun logSignInFailure(cause: Throwable) {
        when (cause) {
            is HttpException -> {
                val body = runCatching { cause.response()?.errorBody()?.string() }.getOrNull()
                Log.e(TAG, "Backend sign-in HTTP ${cause.code()} ${cause.message()} — body=$body", cause)
            }
            is IOException ->
                Log.e(TAG, "Backend sign-in network failure (no connectivity / timeout)", cause)
            else ->
                Log.e(TAG, "Backend sign-in failed: ${cause.javaClass.simpleName} / ${cause.message}", cause)
        }
    }

    /**
     * A short, user-safe diagnostic label for a sign-in failure — shown on the
     * login screen so a field user can report it. Carries no token/credential
     * material: an HTTP status, a coarse network label, or an exception class name.
     */
    fun describeSignInFailure(cause: Throwable): String = when (cause) {
        is HttpException -> "backend ${cause.code()}"
        is IOException -> "network unavailable"
        else -> cause.javaClass.simpleName
    }

    suspend fun signOut() {
        // Drop this device's push token while the session is still valid — the
        // unregister call is authenticated, so it must run before clearing tokens.
        deviceRegistrar.unregister()
        tokenStore.clear()
    }
}

private fun UserSummaryDto.toUserSession() = UserSession(
    id = id,
    displayName = displayName,
    email = email,
    photoUrl = photoUrl,
    avatarColor = avatarColor,
)
