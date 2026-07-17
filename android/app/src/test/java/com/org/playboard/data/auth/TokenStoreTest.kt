package com.org.playboard.data.auth

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.org.playboard.data.model.SessionState
import com.org.playboard.data.model.UserSession
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class TokenStoreTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var tokenStore: TokenStore

    @Before
    fun setUp() {
        val dataStore: DataStore<Preferences> =
            PreferenceDataStoreFactory.create(produceFile = { tempFolder.newFile("test.preferences_pb") })
        tokenStore = TokenStore(dataStore)
    }

    @Test
    fun `starts signed out`() = runTest {
        assertEquals(SessionState.SignedOut, tokenStore.sessionState.first())
        assertNull(tokenStore.currentAccessToken())
    }

    @Test
    fun `save then read round trips the session`() = runTest {
        val user = UserSession(
            id = "user-1",
            displayName = "Raj",
            email = "raj@example.com",
            photoUrl = null,
            avatarId = null,
            avatarColor = "#7ED321",
        )

        tokenStore.save(accessToken = "access-1", refreshToken = "refresh-1", user = user)

        assertEquals("access-1", tokenStore.currentAccessToken())
        assertEquals("refresh-1", tokenStore.currentRefreshToken())
        val state = tokenStore.sessionState.first()
        assertTrue(state is SessionState.SignedIn)
        assertEquals(user, (state as SessionState.SignedIn).user)
    }

    @Test
    fun `updateTokens changes only the tokens, not the user`() = runTest {
        val user = UserSession("user-1", "Raj", "raj@example.com", null, null, "#7ED321")
        tokenStore.save("access-1", "refresh-1", user)

        tokenStore.updateTokens("access-2", "refresh-2")

        assertEquals("access-2", tokenStore.currentAccessToken())
        assertEquals("refresh-2", tokenStore.currentRefreshToken())
        val state = tokenStore.sessionState.first() as SessionState.SignedIn
        assertEquals(user, state.user)
    }

    @Test
    fun `clear signs out`() = runTest {
        val user = UserSession("user-1", "Raj", "raj@example.com", null, null, "#7ED321")
        tokenStore.save("access-1", "refresh-1", user)

        tokenStore.clear()

        assertEquals(SessionState.SignedOut, tokenStore.sessionState.first())
        assertNull(tokenStore.currentAccessToken())
    }
}
