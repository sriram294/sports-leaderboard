package com.org.playboard.data.device

import com.org.playboard.data.remote.PlayboardApi
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class DeviceRegistrarTest {

    private lateinit var server: MockWebServer
    private lateinit var registrar: DeviceRegistrar

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val json = Json { ignoreUnknownKeys = true }
        val api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(PlayboardApi::class.java)
        registrar = DeviceRegistrar(api)
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `registerToken posts the token to the devices endpoint`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(204))

        registrar.registerToken("fcm-token-123")

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/v1/devices", request.path)
        assertTrue(request.body.readUtf8().contains("fcm-token-123"))
    }

    @Test
    fun `registerToken swallows backend errors`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500))

        // Best-effort: a failed registration must not throw.
        registrar.registerToken("fcm-token-123")
    }
}
