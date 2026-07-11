package com.org.playboard.ui.login

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.org.playboard.data.auth.AuthRepository
import com.org.playboard.data.auth.GoogleAuthClient
import com.org.playboard.data.auth.GoogleAuthResult
import com.org.playboard.data.auth.TokenStore
import com.org.playboard.data.remote.PlayboardApi
import com.org.playboard.data.remote.dto.CreateGroupRequestDto
import com.org.playboard.data.remote.dto.CreateInviteRequestDto
import com.org.playboard.data.remote.dto.GoogleSignInRequestDto
import com.org.playboard.data.remote.dto.GroupDto
import com.org.playboard.data.remote.dto.GroupsResponseDto
import com.org.playboard.data.remote.dto.InviteResponseDto
import com.org.playboard.data.remote.dto.JoinGroupRequestDto
import com.org.playboard.data.remote.dto.RenameGroupRequestDto
import com.org.playboard.data.remote.dto.LeaderboardResponseDto
import com.org.playboard.data.remote.dto.MembersResponseDto
import com.org.playboard.data.remote.dto.PlayerStatsDto
import com.org.playboard.data.remote.dto.RecordMatchRequestDto
import com.org.playboard.data.remote.dto.MatchDetailDto
import com.org.playboard.data.remote.dto.MatchListResponseDto
import com.org.playboard.data.remote.dto.RecordMatchResponseDto
import com.org.playboard.data.remote.dto.RefreshRequestDto
import com.org.playboard.data.remote.dto.TokenResponseDto
import com.org.playboard.data.remote.dto.UserSummaryDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

private class FakeGoogleAuthClient(private val result: GoogleAuthResult) : GoogleAuthClient {
    override suspend fun signIn(): GoogleAuthResult = result
}

private class FakePlayboardApi(private val signInResult: suspend (GoogleSignInRequestDto) -> TokenResponseDto) :
    PlayboardApi {
    override suspend fun signInWithGoogle(request: GoogleSignInRequestDto): TokenResponseDto = signInResult(request)
    override suspend fun refresh(request: RefreshRequestDto): TokenResponseDto = error("not used in this test")
    override suspend fun getGroups(): GroupsResponseDto = error("not used in this test")
    override suspend fun createGroup(request: CreateGroupRequestDto): GroupDto = error("not used in this test")
    override suspend fun joinGroup(request: JoinGroupRequestDto): GroupDto = error("not used in this test")
    override suspend fun renameGroup(groupId: String, request: RenameGroupRequestDto): GroupDto = error("not used in this test")
    override suspend fun createInvite(groupId: String, request: CreateInviteRequestDto): InviteResponseDto =
        error("not used in this test")
    override suspend fun getMembers(groupId: String): MembersResponseDto = error("not used in this test")
    override suspend fun getPlayerStats(groupId: String, userId: String): PlayerStatsDto = error("not used in this test")
    override suspend fun recordMatch(groupId: String, request: RecordMatchRequestDto): RecordMatchResponseDto =
        error("not used in this test")
    override suspend fun getMatches(groupId: String, cursor: String?, limit: Int?): MatchListResponseDto =
        error("not used in this test")
    override suspend fun getMatchDetail(groupId: String, matchId: String): MatchDetailDto =
        error("not used in this test")
    override suspend fun editMatch(groupId: String, matchId: String, request: RecordMatchRequestDto): MatchDetailDto =
        error("not used in this test")
    override suspend fun deleteMatch(groupId: String, matchId: String) = error("not used in this test")
    override suspend fun getLeaderboard(groupId: String): LeaderboardResponseDto = error("not used in this test")
}

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var tokenStore: TokenStore

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        // DataStore's internal write actor defaults to a real Dispatchers.IO
        // scope, which runTest's virtual clock can't fast-forward through —
        // pin it to the same (virtual) test dispatcher so advanceUntilIdle()
        // actually waits for writes triggered inside viewModelScope.launch.
        val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(testDispatcher),
            produceFile = { tempFolder.newFile("test.preferences_pb") },
        )
        tokenStore = TokenStore(dataStore)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `successful sign in clears loading and error`() = runTest(testDispatcher) {
        val api = FakePlayboardApi {
            TokenResponseDto(
                "access-1",
                "refresh-1",
                900,
                UserSummaryDto("user-1", "Raj", "raj@example.com", null, "#7ED321"),
            )
        }
        val viewModel = LoginViewModel(
            googleAuthClient = FakeGoogleAuthClient(GoogleAuthResult.Success("id-token")),
            authRepository = AuthRepository(api, tokenStore),
        )

        viewModel.onContinueWithGoogleClicked()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(false, state.isLoading)
        assertNull(state.error)
    }

    @Test
    fun `backend failure surfaces a generic error`() = runTest(testDispatcher) {
        val api = FakePlayboardApi { throw RuntimeException("network error") }
        val viewModel = LoginViewModel(
            googleAuthClient = FakeGoogleAuthClient(GoogleAuthResult.Success("id-token")),
            authRepository = AuthRepository(api, tokenStore),
        )

        viewModel.onContinueWithGoogleClicked()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(false, state.isLoading)
        assertEquals(LoginError.Generic, state.error)
    }

    @Test
    fun `no google account surfaces a specific error`() = runTest(testDispatcher) {
        val viewModel = LoginViewModel(
            googleAuthClient = FakeGoogleAuthClient(GoogleAuthResult.NoCredentialAvailable),
            authRepository = AuthRepository(FakePlayboardApi { error("not called") }, tokenStore),
        )

        viewModel.onContinueWithGoogleClicked()
        advanceUntilIdle()

        assertEquals(LoginError.NoGoogleAccount, viewModel.uiState.value.error)
    }

    @Test
    fun `cancelled sign in leaves state idle without an error`() = runTest(testDispatcher) {
        val viewModel = LoginViewModel(
            googleAuthClient = FakeGoogleAuthClient(GoogleAuthResult.Cancelled),
            authRepository = AuthRepository(FakePlayboardApi { error("not called") }, tokenStore),
        )

        viewModel.onContinueWithGoogleClicked()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(false, state.isLoading)
        assertNull(state.error)
    }
}
