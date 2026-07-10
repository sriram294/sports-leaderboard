package com.org.playboard.di

import com.org.playboard.BuildConfig
import com.org.playboard.data.remote.AuthInterceptor
import com.org.playboard.data.remote.PlayboardApi
import com.org.playboard.data.remote.TokenAuthenticator
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/** Unauthenticated client — sign-in and token refresh only. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AuthApi

/** Bearer-authenticated client, with refresh-on-401 — every other endpoint. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AuthenticatedApi

/**
 * Two separate Retrofit/OkHttp stacks, not one, to avoid a circular
 * dependency: the authenticated client's [TokenAuthenticator] itself needs
 * to call `/auth/refresh`, which can't go through a client that depends on
 * the authenticator that depends on it. The plain [AuthApi] client breaks
 * that cycle and is also just correct on its own terms — the refresh call
 * shouldn't trigger another auth challenge recursively.
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json { ignoreUnknownKeys = true }

    private fun baseClientBuilder(): OkHttpClient.Builder =
        OkHttpClient.Builder().apply {
            // The backend runs on Railway's free tier, which sleeps when idle and
            // can take ~15-20s to cold-start on the first request. OkHttp's default
            // 10s read timeout is too tight for that and fails the first sign-in;
            // a 30s call timeout (whole request/response) absorbs the wake-up.
            callTimeout(30, TimeUnit.SECONDS)
            if (BuildConfig.DEBUG) {
                addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY })
            }
        }

    @Provides
    @Singleton
    @AuthApi
    fun provideAuthOkHttpClient(): OkHttpClient = baseClientBuilder().build()

    @Provides
    @Singleton
    @AuthenticatedApi
    fun provideAuthenticatedOkHttpClient(
        authInterceptor: AuthInterceptor,
        tokenAuthenticator: TokenAuthenticator,
    ): OkHttpClient = baseClientBuilder()
        .addInterceptor(authInterceptor)
        .authenticator(tokenAuthenticator)
        .build()

    private fun retrofit(client: OkHttpClient, json: Json): Retrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.BASE_URL)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    @Provides
    @Singleton
    @AuthApi
    fun provideAuthRetrofit(@AuthApi client: OkHttpClient, json: Json): Retrofit = retrofit(client, json)

    @Provides
    @Singleton
    @AuthenticatedApi
    fun provideAuthenticatedRetrofit(@AuthenticatedApi client: OkHttpClient, json: Json): Retrofit =
        retrofit(client, json)

    @Provides
    @Singleton
    @AuthApi
    fun provideAuthApi(@AuthApi retrofit: Retrofit): PlayboardApi = retrofit.create(PlayboardApi::class.java)

    @Provides
    @Singleton
    @AuthenticatedApi
    fun provideAuthenticatedApi(@AuthenticatedApi retrofit: Retrofit): PlayboardApi =
        retrofit.create(PlayboardApi::class.java)
}
