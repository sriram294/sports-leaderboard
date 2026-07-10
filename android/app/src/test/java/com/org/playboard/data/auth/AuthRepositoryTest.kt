package com.org.playboard.data.auth

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.org.playboard.data.model.SessionState
import com.org.playboard.data.remote.PlayboardApi
import com.org.playboard.data.remote.dto.CreateGroupRequestDto
import com.org.playboard.data.remote.dto.CreateInviteRequestDto
import com.org.playboard.data.remote.dto.GoogleSignInRequestDto
import com.org.playboard.data.remote.dto.GroupDto
import com.org.playboard.data.remote.dto.GroupsResponseDto
import com.org.playboard.data.remote.dto.InviteResponseDto
import com.org.playboard.data.remote.dto.JoinGroupRequestDto
import com.org.playboard.data.remote.dto.LeaderboardResponseDto
import com.org.playboard.data.remote.dto.MembersResponseDto
import com.org.playboard.data.remote.dto.RecordMatchRequestDto
import com.org.playboard.data.remote.dto.RecordMatchResponseDto
import com.org.playboard.data.remote.dto.RefreshRequestDto
import com.org.playboard.data.remote.dto.TokenResponseDto
import com.org.playboard.data.remote.dto.UserSummaryDto
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

private class FakePlayboardApi(private val signInResult: suspend (GoogleSignInRequestDto) -> TokenResponseDto) :
    PlayboardApi {
    override suspend fun signInWithGoogle(request: GoogleSignInRequestDto): TokenResponseDto = signInResult(request)
    override suspend fun refresh(request: RefreshRequestDto): TokenResponseDto = error("not used in this test")
    override suspend fun getGroups(): GroupsResponseDto = error("not used in this test")
    override suspend fun createGroup(request: CreateGroupRequestDto): GroupDto = error("not used in this test")
    override suspend fun joinGroup(request: JoinGroupRequestDto): GroupDto = error("not used in this test")
    override suspend fun createInvite(groupId: String, request: CreateInviteRequestDto): InviteResponseDto =
        error("not used in this test")
    override suspend fun getMembers(groupId: String): MembersResponseDto = error("not used in this test")
    override suspend fun recordMatch(groupId: String, request: RecordMatchRequestDto): RecordMatchResponseDto =
        error("not used in this test")
    override suspend fun getLeaderboard(groupId: String): LeaderboardResponseDto = error("not used in this test")
}

class AuthRepositoryTest {

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
    fun `sign in success persists the session`() = runTest {
        val api = FakePlayboardApi {
            TokenResponseDto(
                accessToken = "access-1",
                refreshToken = "refresh-1",
                expiresIn = 900,
                user = UserSummaryDto("user-1", "Raj", "raj@example.com", null, "#7ED321"),
            )
        }
        val repository = AuthRepository(api, tokenStore)

        val result = repository.signInWithGoogle("google-id-token")

        assertTrue(result.isSuccess)
        val state = tokenStore.sessionState.first()
        assertTrue(state is SessionState.SignedIn)
        assertEquals("Raj", (state as SessionState.SignedIn).user.displayName)
    }

    @Test
    fun `sign in failure does not persist a session`() = runTest {
        val api = FakePlayboardApi { throw RuntimeException("network error") }
        val repository = AuthRepository(api, tokenStore)

        val result = repository.signInWithGoogle("google-id-token")

        assertTrue(result.isFailure)
        assertEquals(SessionState.SignedOut, tokenStore.sessionState.first())
    }

    @Test
    fun `sign out clears the session`() = runTest {
        val api = FakePlayboardApi {
            TokenResponseDto(
                "access-1",
                "refresh-1",
                900,
                UserSummaryDto("user-1", "Raj", "raj@example.com", null, "#7ED321"),
            )
        }
        val repository = AuthRepository(api, tokenStore)
        repository.signInWithGoogle("google-id-token")

        repository.signOut()

        assertEquals(SessionState.SignedOut, tokenStore.sessionState.first())
    }
}
