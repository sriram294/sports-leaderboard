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
import com.org.playboard.data.remote.dto.MonthlyTrophyDto
import com.org.playboard.data.remote.dto.PlayerStatsDto
import com.org.playboard.data.remote.dto.RecordMatchRequestDto
import com.org.playboard.data.remote.dto.MatchDetailDto
import com.org.playboard.data.remote.dto.MatchListResponseDto
import com.org.playboard.data.remote.dto.RecordMatchResponseDto
import com.org.playboard.data.remote.dto.RefreshRequestDto
import com.org.playboard.data.remote.dto.ReviewerSignInRequestDto
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import com.org.playboard.data.model.SessionState
import java.io.IOException
import kotlinx.coroutines.flow.first
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.HttpException
import retrofit2.Response

private class FakeGoogleAuthClient(private val result: GoogleAuthResult) : GoogleAuthClient {
    override suspend fun signIn(): GoogleAuthResult = result
}

private class FakePlayboardApi(
    private val reviewerResult: suspend (ReviewerSignInRequestDto) -> TokenResponseDto = { error("not called") },
    private val signInResult: suspend (GoogleSignInRequestDto) -> TokenResponseDto,
) : PlayboardApi {
    val reviewerRequests = mutableListOf<ReviewerSignInRequestDto>()

    
    override suspend fun signInAsReviewer(request: ReviewerSignInRequestDto): TokenResponseDto {
        reviewerRequests += request
        return reviewerResult(request)
    }
    override suspend fun signInWithGoogle(request: GoogleSignInRequestDto): TokenResponseDto = signInResult(request)
    override suspend fun refresh(request: RefreshRequestDto): TokenResponseDto = error("not used in this test")
    override suspend fun getGroups(): GroupsResponseDto = error("not used in this test")
    override suspend fun createGroup(request: CreateGroupRequestDto): GroupDto = error("not used in this test")
    override suspend fun joinGroup(request: JoinGroupRequestDto): GroupDto = error("not used in this test")
    override suspend fun renameGroup(groupId: String, request: RenameGroupRequestDto): GroupDto = error("not used in this test")
    override suspend fun createInvite(groupId: String, request: CreateInviteRequestDto): InviteResponseDto =
        error("not used in this test")
    override suspend fun getMembers(groupId: String): MembersResponseDto = error("not used in this test")
    override suspend fun getGroupTrophies(groupId: String, limit: Int): List<MonthlyTrophyDto> = error("not used in this test")
    override suspend fun addMember(groupId: String, request: com.org.playboard.data.remote.dto.AddMemberRequestDto): com.org.playboard.data.remote.dto.MemberDto = error("not used in this test")
    override suspend fun removeMember(groupId: String, userId: String) = error("unused")
    override suspend fun changeMemberRole(groupId: String, userId: String, request: com.org.playboard.data.remote.dto.UpdateRoleRequestDto): com.org.playboard.data.remote.dto.MemberDto = error("unused")
    override suspend fun updateSession(groupId: String, request: com.org.playboard.data.remote.dto.UpdateSessionRequestDto): com.org.playboard.data.remote.dto.GroupDto = error("unused")
    override suspend fun getPlayerStats(groupId: String, userId: String): PlayerStatsDto = error("not used in this test")
    override suspend fun getPartners(groupId: String, userId: String, from: String?, to: String?): List<com.org.playboard.data.remote.dto.PartnerDto> = error("unused")
    override suspend fun getPlayerAttendance(groupId: String, userId: String, from: String, to: String): com.org.playboard.data.remote.dto.PlayerAttendanceDto = com.org.playboard.data.remote.dto.PlayerAttendanceDto()
    override suspend fun recordMatch(groupId: String, request: RecordMatchRequestDto): RecordMatchResponseDto =
        error("not used in this test")
    override suspend fun getMatches(groupId: String, cursor: String?, limit: Int?, mine: Boolean?): MatchListResponseDto =
        error("not used in this test")
    override suspend fun getMatchDetail(groupId: String, matchId: String): MatchDetailDto =
        error("not used in this test")
    override suspend fun editMatch(groupId: String, matchId: String, request: RecordMatchRequestDto): MatchDetailDto =
        error("not used in this test")
    override suspend fun updateDisplayName(request: com.org.playboard.data.remote.dto.UpdateUserRequestDto): com.org.playboard.data.remote.dto.UserSummaryDto = error("not used in this test")
    override suspend fun uploadUserPhoto(file: okhttp3.MultipartBody.Part): com.org.playboard.data.remote.dto.UserSummaryDto = error("not used in this test")
    override suspend fun updateAvatar(request: com.org.playboard.data.remote.dto.UpdateAvatarRequestDto): com.org.playboard.data.remote.dto.UserSummaryDto = error("not used in this test")
    override suspend fun deleteMatch(groupId: String, matchId: String) = error("not used in this test")
    override suspend fun getLeaderboard(groupId: String, from: String?, to: String?, month: String?): LeaderboardResponseDto = error("not used in this test")
    override suspend fun getLeaderboardMonths(groupId: String): List<String> = emptyList()
    override suspend fun registerDevice(request: com.org.playboard.data.remote.dto.RegisterDeviceRequestDto) = error("not used in this test")
    override suspend fun unregisterDevice(request: com.org.playboard.data.remote.dto.UnregisterDeviceRequestDto) = error("not used in this test")
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
                UserSummaryDto("user-1", "Raj", "raj@example.com", null, null, "#7ED321"),
            )
        }
        val viewModel = LoginViewModel(
            googleAuthClient = FakeGoogleAuthClient(GoogleAuthResult.Success("id-token")),
            authRepository = AuthRepository(api, tokenStore, com.org.playboard.data.device.DeviceRegistrar(api)),
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
            authRepository = AuthRepository(api, tokenStore, com.org.playboard.data.device.DeviceRegistrar(api)),
        )

        viewModel.onContinueWithGoogleClicked()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(false, state.isLoading)
        // The thrown RuntimeException is classified into a short diagnostic code
        // that is surfaced on the login screen for field bug reports.
        assertEquals(LoginError.Generic("RuntimeException"), state.error)
    }

    @Test
    fun `no google account surfaces a specific error`() = runTest(testDispatcher) {
        val viewModel = LoginViewModel(
            googleAuthClient = FakeGoogleAuthClient(GoogleAuthResult.NoCredentialAvailable),
            authRepository = AuthRepository(
                FakePlayboardApi { error("not called") },
                tokenStore,
                com.org.playboard.data.device.DeviceRegistrar(FakePlayboardApi { error("not called") }),
            ),
        )

        viewModel.onContinueWithGoogleClicked()
        advanceUntilIdle()

        assertEquals(LoginError.NoGoogleAccount, viewModel.uiState.value.error)
    }

    @Test
    fun `cancelled sign in leaves state idle without an error`() = runTest(testDispatcher) {
        val viewModel = LoginViewModel(
            googleAuthClient = FakeGoogleAuthClient(GoogleAuthResult.Cancelled),
            authRepository = AuthRepository(
                FakePlayboardApi { error("not called") },
                tokenStore,
                com.org.playboard.data.device.DeviceRegistrar(FakePlayboardApi { error("not called") }),
            ),
        )

        viewModel.onContinueWithGoogleClicked()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(false, state.isLoading)
        assertNull(state.error)
    }

    private fun reviewerViewModel(api: FakePlayboardApi) = LoginViewModel(
        googleAuthClient = FakeGoogleAuthClient(GoogleAuthResult.Cancelled),
        authRepository = AuthRepository(api, tokenStore, com.org.playboard.data.device.DeviceRegistrar(api)),
    )

    private fun tokens() = TokenResponseDto(
        "access-r",
        "refresh-r",
        900,
        UserSummaryDto("reviewer-1", "App Reviewer", "reviewer@reviewer.playboard.invalid", null, null, "#7ED321"),
    )

    @Test
    fun `reviewer login is hidden until the logo is long pressed`() {
        val viewModel = reviewerViewModel(FakePlayboardApi { error("not called") })
        assertFalse(viewModel.uiState.value.isReviewerLoginVisible)

        viewModel.onLogoLongPressed()

        assertTrue(viewModel.uiState.value.isReviewerLoginVisible)
    }

    @Test
    fun `successful reviewer sign in saves the session and clears the password`() = runTest(testDispatcher) {
        val api = FakePlayboardApi(reviewerResult = { tokens() }) { error("not called") }
        val viewModel = reviewerViewModel(api)
        viewModel.onLogoLongPressed()
        viewModel.onReviewerUsernameChanged("  play-reviewer ")
        viewModel.onReviewerPasswordChanged("secret")

        viewModel.onReviewerSignInClicked()
        advanceUntilIdle()

        assertEquals(listOf(ReviewerSignInRequestDto("play-reviewer", "secret")), api.reviewerRequests)
        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNull(state.error)
        assertEquals("", state.reviewerPassword)
        assertTrue(tokenStore.sessionState.first() is SessionState.SignedIn)
    }

    @Test
    fun `rejected reviewer credentials surface a specific error`() = runTest(testDispatcher) {
        val api = FakePlayboardApi(
            reviewerResult = { throw HttpException(Response.error<Any>(401, "".toResponseBody())) },
        ) { error("not called") }
        val viewModel = reviewerViewModel(api)
        viewModel.onLogoLongPressed()
        viewModel.onReviewerUsernameChanged("play-reviewer")
        viewModel.onReviewerPasswordChanged("wrong")

        viewModel.onReviewerSignInClicked()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(LoginError.InvalidReviewerCredentials, state.error)
        assertFalse(state.isLoading)
        assertTrue(state.isReviewerLoginVisible)
        assertEquals("", state.reviewerPassword)
    }

    @Test
    fun `reviewer network failure surfaces a generic error`() = runTest(testDispatcher) {
        val api = FakePlayboardApi(reviewerResult = { throw IOException("offline") }) { error("not called") }
        val viewModel = reviewerViewModel(api)
        viewModel.onReviewerUsernameChanged("play-reviewer")
        viewModel.onReviewerPasswordChanged("secret")

        viewModel.onReviewerSignInClicked()
        advanceUntilIdle()

        assertEquals(LoginError.Generic("network unavailable"), viewModel.uiState.value.error)
    }

    @Test
    fun `reviewer sign in is ignored until both fields are filled`() = runTest(testDispatcher) {
        val api = FakePlayboardApi(reviewerResult = { tokens() }) { error("not called") }
        val viewModel = reviewerViewModel(api)
        viewModel.onReviewerUsernameChanged("play-reviewer")

        viewModel.onReviewerSignInClicked()
        advanceUntilIdle()

        assertTrue(api.reviewerRequests.isEmpty())
        assertFalse(viewModel.uiState.value.canSubmitReviewerLogin)
    }
}
