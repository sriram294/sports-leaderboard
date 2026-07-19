package com.org.playboard.ui.profile

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.org.playboard.data.auth.AuthRepository
import com.org.playboard.data.auth.TokenStore
import com.org.playboard.data.group.GroupRepository
import com.org.playboard.testing.testGroupRepository
import com.org.playboard.data.match.MatchRepository
import com.org.playboard.data.remote.PlayboardApi
import com.org.playboard.data.remote.dto.BestPartnerDto
import com.org.playboard.data.remote.dto.CreateGroupRequestDto
import com.org.playboard.data.remote.dto.CreateInviteRequestDto
import com.org.playboard.data.remote.dto.GoogleSignInRequestDto
import com.org.playboard.data.remote.dto.GroupDto
import com.org.playboard.data.remote.dto.GroupsResponseDto
import com.org.playboard.data.remote.dto.InviteResponseDto
import com.org.playboard.data.remote.dto.JoinGroupRequestDto
import com.org.playboard.data.remote.dto.RenameGroupRequestDto
import com.org.playboard.data.remote.dto.LeaderboardResponseDto
import com.org.playboard.data.remote.dto.MatchDetailDto
import com.org.playboard.data.remote.dto.MatchListResponseDto
import com.org.playboard.data.remote.dto.MatchPlayerDto
import com.org.playboard.data.remote.dto.MatchSetDto
import com.org.playboard.data.remote.dto.MatchSummaryDto
import com.org.playboard.data.remote.dto.MatchTeamDto
import com.org.playboard.data.remote.dto.MembersResponseDto
import com.org.playboard.data.remote.dto.PlayerAttendanceDto
import com.org.playboard.data.remote.dto.PlayerStatsDto
import com.org.playboard.data.remote.dto.RecordMatchRequestDto
import com.org.playboard.data.remote.dto.RecordMatchResponseDto
import com.org.playboard.data.remote.dto.RefreshRequestDto
import com.org.playboard.data.remote.dto.TokenResponseDto
import com.org.playboard.data.remote.dto.UpdateAvatarRequestDto
import com.org.playboard.data.remote.dto.UpdateUserRequestDto
import com.org.playboard.data.remote.dto.UserSummaryDto
import com.org.playboard.data.stats.StatsRepository
import com.org.playboard.data.user.UserRepository
import okhttp3.MultipartBody
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

private open class FakePlayboardApi(
    var groups: List<GroupDto> = emptyList(),
    var stats: Map<String, PlayerStatsDto> = emptyMap(),
    var attendance: Map<String, PlayerAttendanceDto> = emptyMap(),
) : PlayboardApi {
    override suspend fun getAppUpdate(): com.org.playboard.data.remote.dto.AppUpdateDto = error("not used in this test")
    override suspend fun downloadApk(url: String): okhttp3.ResponseBody = error("not used in this test")
    var statsCalls = 0
    var userName = "Raj"
    var userPhotoUrl: String? = null
    var userAvatarId: String? = null
    var photoUploads = 0
    var avatarUpdates = 0

    private fun currentUser() = UserSummaryDto("u1", userName, "raj@example.com", userPhotoUrl, userAvatarId, "#9ADE28")

    override suspend fun signInWithGoogle(request: GoogleSignInRequestDto): TokenResponseDto =
        TokenResponseDto("access", "refresh", 900, currentUser())
    override suspend fun updateDisplayName(request: UpdateUserRequestDto): UserSummaryDto {
        userName = request.displayName
        return currentUser()
    }
    override suspend fun uploadUserPhoto(file: MultipartBody.Part): UserSummaryDto {
        photoUploads++
        userPhotoUrl = "https://cdn.example/avatars/u1.png"
        userAvatarId = null
        return currentUser()
    }
    override suspend fun updateAvatar(request: UpdateAvatarRequestDto): UserSummaryDto {
        avatarUpdates++
        userAvatarId = request.avatarId
        userPhotoUrl = null
        return currentUser()
    }
    override suspend fun refresh(request: RefreshRequestDto): TokenResponseDto = error("unused")
    override suspend fun getGroups(): GroupsResponseDto = GroupsResponseDto(groups)
    override suspend fun createGroup(request: CreateGroupRequestDto): GroupDto = error("unused")
    override suspend fun joinGroup(request: JoinGroupRequestDto): GroupDto = error("unused")
    override suspend fun renameGroup(groupId: String, request: RenameGroupRequestDto): GroupDto = error("not used in this test")
    override suspend fun createInvite(groupId: String, request: CreateInviteRequestDto): InviteResponseDto = error("unused")
    override suspend fun getLeaderboard(groupId: String, from: String?, to: String?): LeaderboardResponseDto = error("unused")
    override suspend fun registerDevice(request: com.org.playboard.data.remote.dto.RegisterDeviceRequestDto) = error("unused")
    override suspend fun unregisterDevice(request: com.org.playboard.data.remote.dto.UnregisterDeviceRequestDto) = error("unused")
    override suspend fun getMembers(groupId: String): MembersResponseDto = error("unused")
    override suspend fun addMember(groupId: String, request: com.org.playboard.data.remote.dto.AddMemberRequestDto): com.org.playboard.data.remote.dto.MemberDto = error("unused")
    override suspend fun removeMember(groupId: String, userId: String) = error("unused")
    override suspend fun changeMemberRole(groupId: String, userId: String, request: com.org.playboard.data.remote.dto.UpdateRoleRequestDto): com.org.playboard.data.remote.dto.MemberDto = error("unused")
    override suspend fun updateSession(groupId: String, request: com.org.playboard.data.remote.dto.UpdateSessionRequestDto): com.org.playboard.data.remote.dto.GroupDto = error("unused")
    override suspend fun getPlayerStats(groupId: String, userId: String): PlayerStatsDto {
        statsCalls++
        return stats[userId] ?: error("no stats for $userId")
    }
    override suspend fun getPlayerAttendance(groupId: String, userId: String, from: String, to: String): PlayerAttendanceDto =
        attendance[userId] ?: PlayerAttendanceDto()
    override suspend fun recordMatch(groupId: String, request: RecordMatchRequestDto): RecordMatchResponseDto = error("unused")
    override suspend fun getMatches(groupId: String, cursor: String?, limit: Int?, mine: Boolean?): MatchListResponseDto = error("unused")
    override suspend fun getMatchDetail(groupId: String, matchId: String): MatchDetailDto = error("unused")
    override suspend fun editMatch(groupId: String, matchId: String, request: RecordMatchRequestDto): MatchDetailDto = error("unused")
    override suspend fun deleteMatch(groupId: String, matchId: String) = error("unused")
}

private fun groupDto() = GroupDto(
    id = "g1",
    name = "Smashers",
    avatarColor = "#C7EA2B",
    sportCode = "badminton_doubles",
    memberCount = 4,
    matchCount = 2,
    myRole = "member",
)

private fun player(id: String, name: String) = MatchPlayerDto(id, name, "#FF3D8A", null)

private fun statsDto(userId: String = "u1", displayName: String = "Raj", withPartner: Boolean = true) = PlayerStatsDto(
    userId = userId,
    displayName = displayName,
    photoUrl = null,
    avatarColor = "#9ADE28",
    matchesPlayed = 8,
    wins = 4,
    losses = 4,
    pointsFor = 315,
    pointsAgainst = 320,
    winRate = 0.5,
    currentStreak = 2,
    bestStreak = 3,
    bestPartner = if (withPartner) BestPartnerDto("u2", "Dev", null, null, "#3DB4FF", 2, 2, 1.0) else null,
    recentMatches = listOf(
        MatchSummaryDto(
            id = "m1",
            playedAt = "2026-07-09T06:58:00Z",
            teams = listOf(
                MatchTeamDto(1, true, listOf(player("u1", "Raj"), player("u2", "Dev"))),
                MatchTeamDto(2, false, listOf(player("u3", "Marcus"), player("u4", "Kiran"))),
            ),
            sets = listOf(MatchSetDto(1, 21, 12), MatchSetDto(2, 21, 17)),
        ),
    ),
)

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private suspend fun readyViewModel(api: FakePlayboardApi): Triple<ProfileViewModel, AuthRepository, GroupRepository> {
        val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(testDispatcher),
            produceFile = { tempFolder.newFile("ds-${System.nanoTime()}.preferences_pb") },
        )
        val json = Json { ignoreUnknownKeys = true }
        val tokenStore = TokenStore(dataStore)
        val auth = AuthRepository(api, tokenStore, com.org.playboard.data.device.DeviceRegistrar(api))
        val groups = testGroupRepository(api, json)
        val stats = StatsRepository(api)
        val user = UserRepository(api, tokenStore)
        auth.signInWithGoogle("token")
        groups.refreshGroups()
        return Triple(ProfileViewModel(auth, groups, stats, user), auth, groups)
    }

    @Test
    fun `loads own stats for the active group`() = runTest(testDispatcher) {
        val api = FakePlayboardApi(groups = listOf(groupDto()), stats = mapOf("u1" to statsDto()))
        val (viewModel, _, _) = readyViewModel(api)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals("Smashers", state.groupName)
        assertEquals("raj@example.com", state.email)
        assertTrue(state.isOwnProfile)
        assertEquals(50, state.stats?.winRatePercent)
        assertEquals(8, state.stats?.matchesPlayed)
        assertEquals("Dev", state.stats?.bestPartner?.displayName)
    }

    @Test
    fun `recent matches are framed from the viewed player's side`() = runTest(testDispatcher) {
        val api = FakePlayboardApi(groups = listOf(groupDto()), stats = mapOf("u1" to statsDto()))
        val (viewModel, _, _) = readyViewModel(api)
        advanceUntilIdle()

        val row = viewModel.uiState.value.recentMatches.single()
        assertTrue(row.isWin)                       // u1 is on the winning team 1
        assertEquals("Dev", row.partnerNames)
        assertEquals("Marcus & Kiran", row.opponentNames)
        assertEquals(2, row.sets.size)
    }

    @Test
    fun `no active group shows the empty state`() = runTest(testDispatcher) {
        val api = FakePlayboardApi(groups = emptyList(), stats = mapOf("u1" to statsDto()))
        val (viewModel, _, _) = readyViewModel(api)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertTrue(state.noGroup)
        assertNull(state.stats)
    }

    @Test
    fun `a match change bumps the revision and refreshes silently`() = runTest(testDispatcher) {
        val api = FakePlayboardApi(groups = listOf(groupDto()), stats = mapOf("u1" to statsDto()))
        val (viewModel, _, groups) = readyViewModel(api)
        advanceUntilIdle()
        val callsAfterInitialLoad = api.statsCalls

        groups.notifyMatchesChanged()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals(callsAfterInitialLoad + 1, api.statsCalls)
    }

    @Test
    fun `viewing another player loads their stats and hides the account section`() = runTest(testDispatcher) {
        val api = FakePlayboardApi(
            groups = listOf(groupDto()),
            stats = mapOf(
                "u1" to statsDto("u1", "Raj"),
                "u2" to statsDto("u2", "Dev"),
            ),
        )
        val (viewModel, _, _) = readyViewModel(api)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isOwnProfile) // own profile by default

        viewModel.setViewedUser("u2")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isOwnProfile)                 // account section hidden
        assertEquals("u2", state.stats?.userId)
        assertEquals("Dev", state.stats?.displayName)
    }

    @Test
    fun `returning to own profile restores the account section`() = runTest(testDispatcher) {
        val api = FakePlayboardApi(
            groups = listOf(groupDto()),
            stats = mapOf(
                "u1" to statsDto("u1", "Raj"),
                "u2" to statsDto("u2", "Dev"),
            ),
        )
        val (viewModel, _, _) = readyViewModel(api)
        advanceUntilIdle()

        viewModel.setViewedUser("u2")
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isOwnProfile)

        viewModel.setViewedUser(null)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.isOwnProfile)
        assertEquals("u1", state.stats?.userId)
    }

    @Test
    fun `bestPartner may be absent`() = runTest(testDispatcher) {
        val api = FakePlayboardApi(groups = listOf(groupDto()), stats = mapOf("u1" to statsDto(withPartner = false)))
        val (viewModel, _, _) = readyViewModel(api)
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.stats?.bestPartner)
    }

    @Test
    fun `loads attendance over the last 3 months, bucketed into local days`() = runTest(testDispatcher) {
        val zone = java.time.ZoneId.systemDefault()
        val today = java.time.LocalDate.now(zone)
        // One match this month and one ~2 months back — both inside the heatmap window.
        val recent = today.withDayOfMonth(1).atTime(12, 0).atZone(zone).toInstant()
        val older = today.minusMonths(2).withDayOfMonth(2).atTime(12, 0).atZone(zone).toInstant()
        val api = FakePlayboardApi(
            groups = listOf(groupDto()),
            stats = mapOf("u1" to statsDto()),
            attendance = mapOf("u1" to PlayerAttendanceDto(listOf(recent.toString(), older.toString()))),
        )
        val (viewModel, _, _) = readyViewModel(api)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(3, state.attendanceMonths.size)
        assertEquals(java.time.YearMonth.from(today), state.attendanceMonths.last())
        assertEquals(
            setOf(today.withDayOfMonth(1), today.minusMonths(2).withDayOfMonth(2)),
            state.attendanceDays,
        )
    }

    @Test
    fun `an attendance failure still renders stats`() = runTest(testDispatcher) {
        val api = object : FakePlayboardApi(groups = listOf(groupDto()), stats = mapOf("u1" to statsDto())) {
            override suspend fun getPlayerAttendance(groupId: String, userId: String, from: String, to: String): PlayerAttendanceDto =
                error("attendance down")
        }
        val (viewModel, _, _) = readyViewModel(api)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.hasLoadFailed)
        assertEquals(8, state.stats?.matchesPlayed)
        assertTrue(state.attendanceDays.isEmpty())
    }

    @Test
    fun `editing the name opens a seeded sheet, saves, and updates the identity`() = runTest(testDispatcher) {
        val api = FakePlayboardApi(groups = listOf(groupDto()), stats = mapOf("u1" to statsDto()))
        val (viewModel, _, _) = readyViewModel(api)
        advanceUntilIdle()

        viewModel.onEditNameClicked()
        assertEquals("Raj", viewModel.uiState.value.renameSheet?.input) // seeded with current name

        viewModel.onRenameInputChanged("Raj K")
        viewModel.onRenameSubmitted()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNull(state.renameSheet)                 // sheet closed on success
        assertEquals("Raj K", state.displayName)       // identity reflects the live session name
        assertEquals("Raj K", api.userName)            // persisted server-side
    }

    @Test
    fun `a failed rename keeps the sheet open and flags the error`() = runTest(testDispatcher) {
        val api = object : FakePlayboardApi(groups = listOf(groupDto()), stats = mapOf("u1" to statsDto())) {
            override suspend fun updateDisplayName(request: UpdateUserRequestDto): UserSummaryDto = error("network down")
        }
        val (viewModel, _, _) = readyViewModel(api)
        advanceUntilIdle()

        viewModel.onEditNameClicked()
        viewModel.onRenameInputChanged("Raj K")
        viewModel.onRenameSubmitted()
        advanceUntilIdle()

        val sheet = viewModel.uiState.value.renameSheet
        assertTrue(sheet != null && sheet.hasFailed && !sheet.isSubmitting)
        assertEquals("Raj", viewModel.uiState.value.displayName) // unchanged
    }

    @Test
    fun `uploading a photo sets the identity avatar and clears the spinner`() = runTest(testDispatcher) {
        val api = FakePlayboardApi(groups = listOf(groupDto()), stats = mapOf("u1" to statsDto()))
        val (viewModel, _, _) = readyViewModel(api)
        advanceUntilIdle()

        viewModel.onPhotoSelected(byteArrayOf(1, 2, 3), "image/png")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isUploadingPhoto)
        assertEquals(1, api.photoUploads)
        // Cache-busted URL so the image loader reloads the new bytes.
        assertTrue(state.identityPhotoUrl?.startsWith("https://cdn.example/avatars/u1.png?") == true)
    }

    @Test
    fun `selecting a default avatar sets the identity avatar and clears any photo`() = runTest(testDispatcher) {
        val api = FakePlayboardApi(groups = listOf(groupDto()), stats = mapOf("u1" to statsDto()))
        val (viewModel, _, _) = readyViewModel(api)
        advanceUntilIdle()
        // Start from an uploaded photo so we can prove picking an avatar clears it.
        viewModel.onPhotoSelected(byteArrayOf(1, 2, 3), "image/png")
        advanceUntilIdle()

        viewModel.onAvatarSelected("avatar0")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isUploadingPhoto)
        assertEquals(1, api.avatarUpdates)
        assertEquals("avatar0", state.identityAvatarId)
        assertNull(state.identityPhotoUrl) // photo replaced by the avatar (exclusive)
    }
}
