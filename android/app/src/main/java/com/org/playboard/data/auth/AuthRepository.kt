package com.org.playboard.data.auth

import com.org.playboard.data.device.DeviceRegistrar
import com.org.playboard.data.model.SessionState
import com.org.playboard.data.model.UserSession
import com.org.playboard.data.remote.PlayboardApi
import com.org.playboard.data.remote.dto.GoogleSignInRequestDto
import com.org.playboard.data.remote.dto.UserSummaryDto
import com.org.playboard.di.AuthApi
import javax.inject.Inject
import javax.inject.Singleton
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

    val sessionState: StateFlow<SessionState> =
        tokenStore.sessionState.stateIn(repositoryScope, SharingStarted.Eagerly, SessionState.Loading)

    suspend fun signInWithGoogle(idToken: String): Result<Unit> = runCatching {
        val response = authApi.signInWithGoogle(GoogleSignInRequestDto(idToken))
        val user = requireNotNull(response.user) { "Sign-in response missing user" }
        tokenStore.save(response.accessToken, response.refreshToken, user.toUserSession())
        // Best-effort, off the sign-in path: the session is now valid, so the
        // authenticated register call will carry a bearer token.
        repositoryScope.launch { deviceRegistrar.register() }
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
