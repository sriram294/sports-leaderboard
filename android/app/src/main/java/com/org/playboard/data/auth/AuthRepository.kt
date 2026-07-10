package com.org.playboard.data.auth

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

@Singleton
class AuthRepository @Inject constructor(
    @AuthApi private val authApi: PlayboardApi,
    private val tokenStore: TokenStore,
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
    }

    suspend fun signOut() {
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
