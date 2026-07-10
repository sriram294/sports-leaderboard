package com.org.playboard.data.remote

import com.org.playboard.data.auth.TokenStore
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response

/** Attaches `Authorization: Bearer <accessToken>` to every request on the authenticated client. */
class AuthInterceptor @Inject constructor(private val tokenStore: TokenStore) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val accessToken = runBlocking { tokenStore.currentAccessToken() }
        val original = chain.request()
        val request = if (accessToken != null) {
            original.newBuilder().header("Authorization", "Bearer $accessToken").build()
        } else {
            original
        }
        return chain.proceed(request)
    }
}
