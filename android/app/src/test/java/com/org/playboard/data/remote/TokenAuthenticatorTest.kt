package com.org.playboard.data.remote

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.org.playboard.data.auth.TokenStore
import com.org.playboard.data.model.UserSession
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class TokenAuthenticatorTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var server: MockWebServer
    private lateinit var tokenStore: TokenStore
    private lateinit var authApi: PlayboardApi

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()

        val dataStore: DataStore<Preferences> =
            PreferenceDataStoreFactory.create(produceFile = { tempFolder.newFile("test.preferences_pb") })
        tokenStore = TokenStore(dataStore)

        val json = Json { ignoreUnknownKeys = true }
        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
        authApi = retrofit.create(PlayboardApi::class.java)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `refreshes once on 401 and retries the original request`() = runBlocking {
        tokenStore.save(
            accessToken = "stale-token",
            refreshToken = "refresh-token",
            user = UserSession("user-1", "Raj", "raj@example.com", null, "#7ED321"),
        )

        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody("""{"accessToken":"fresh-token","refreshToken":"fresh-refresh","expiresIn":900}"""),
        )
        server.enqueue(MockResponse().setResponseCode(200).setBody("ok"))

        val client = OkHttpClient.Builder()
            .authenticator(TokenAuthenticator(tokenStore, authApi))
            .build()

        val response = client.newCall(
            Request.Builder()
                .url(server.url("/api/v1/protected"))
                .header("Authorization", "Bearer stale-token")
                .build(),
        ).execute()

        assertEquals(200, response.code)
        assertEquals("fresh-token", tokenStore.currentAccessToken())
        assertEquals("fresh-refresh", tokenStore.currentRefreshToken())

        server.takeRequest() // the original request that 401'd
        val refreshCall = server.takeRequest()
        assertEquals("/api/v1/auth/refresh", refreshCall.path)
        val retriedRequest = server.takeRequest()
        assertEquals("Bearer fresh-token", retriedRequest.getHeader("Authorization"))
    }

    @Test
    fun `clears session when refresh itself fails, without looping`() = runBlocking {
        tokenStore.save(
            accessToken = "stale-token",
            refreshToken = "refresh-token",
            user = UserSession("user-1", "Raj", "raj@example.com", null, "#7ED321"),
        )

        server.enqueue(MockResponse().setResponseCode(401)) // original request
        server.enqueue(MockResponse().setResponseCode(401)) // refresh call itself fails

        val client = OkHttpClient.Builder()
            .authenticator(TokenAuthenticator(tokenStore, authApi))
            .build()

        val response = client.newCall(
            Request.Builder()
                .url(server.url("/api/v1/protected"))
                .header("Authorization", "Bearer stale-token")
                .build(),
        ).execute()

        // Authenticator gave up (returned null) — the original 401 surfaces, no infinite retry.
        assertEquals(401, response.code)
        assertNull(tokenStore.currentAccessToken())
        assertEquals(2, server.requestCount)
    }
}
