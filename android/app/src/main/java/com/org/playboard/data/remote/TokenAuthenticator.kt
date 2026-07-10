package com.org.playboard.data.remote

import com.org.playboard.data.auth.TokenStore
import com.org.playboard.data.remote.dto.RefreshRequestDto
import com.org.playboard.di.AuthApi
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

/**
 * On a 401, refreshes the access token once and retries — the standard
 * OkHttp idiom for this (an [Authenticator], not an [Interceptor], since it
 * needs to synchronously produce a retried [Request]). Guarded against
 * concurrent duplicate refreshes ([mutex]) and infinite retry loops
 * ([responseCount]). On refresh failure, clears the session — the
 * [com.org.playboard.data.auth.AuthRepository.sessionState] flow this
 * flips will route the UI back to Login.
 */
class TokenAuthenticator @Inject constructor(
    private val tokenStore: TokenStore,
    @AuthApi private val authApi: PlayboardApi,
) : Authenticator {

    private val mutex = Mutex()

    override fun authenticate(route: Route?, response: Response): Request? {
        if (responseCount(response) >= 2) return null

        val failedAccessToken = response.request.header("Authorization")?.removePrefix("Bearer ")

        return runBlocking {
            mutex.withLock {
                val currentAccessToken = tokenStore.currentAccessToken()
                // Another request already refreshed while we waited for the lock — reuse it.
                if (currentAccessToken != null && currentAccessToken != failedAccessToken) {
                    return@withLock response.request.newBuilder()
                        .header("Authorization", "Bearer $currentAccessToken")
                        .build()
                }

                val refreshToken = tokenStore.currentRefreshToken()
                if (refreshToken == null) {
                    tokenStore.clear()
                    return@withLock null
                }

                try {
                    val tokenResponse = authApi.refresh(RefreshRequestDto(refreshToken))
                    tokenStore.updateTokens(tokenResponse.accessToken, tokenResponse.refreshToken)
                    response.request.newBuilder()
                        .header("Authorization", "Bearer ${tokenResponse.accessToken}")
                        .build()
                } catch (e: Exception) {
                    tokenStore.clear()
                    null
                }
            }
        }
    }

    private fun responseCount(response: Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }
}
