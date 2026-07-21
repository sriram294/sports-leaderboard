package com.org.playboard.ui.add

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.org.playboard.data.auth.AuthRepository
import com.org.playboard.data.auth.TokenStore
import com.org.playboard.data.group.GroupRepository
import com.org.playboard.testing.testGroupRepository
import com.org.playboard.data.match.MatchRepository
import com.org.playboard.data.model.Member
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
import com.org.playboard.data.remote.dto.MatchDetailDto
import com.org.playboard.data.remote.dto.MatchListResponseDto
import com.org.playboard.data.remote.dto.MatchPlayerDto
import com.org.playboard.data.remote.dto.MatchSetDto
import com.org.playboard.data.remote.dto.MatchTeamDto
import com.org.playboard.data.remote.dto.RecordedByDto
import com.org.playboard.data.remote.dto.MemberDto
import com.org.playboard.data.remote.dto.MembersResponseDto
import com.org.playboard.data.remote.dto.MonthlyTrophyDto
import com.org.playboard.data.remote.dto.PlayerStatsDto
import com.org.playboard.data.remote.dto.RecordMatchRequestDto
import com.org.playboard.data.remote.dto.RecordMatchResponseDto
import com.org.playboard.data.remote.dto.RefreshRequestDto
import com.org.playboard.data.remote.dto.TokenResponseDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import retrofit2.HttpException
import retrofit2.Response

private class FakePlayboardApi(
    var groups: List<GroupDto> = emptyList(),
    var members: List<MemberDto> = emptyList(),
    var recordMatchBehavior: suspend (RecordMatchRequestDto) -> RecordMatchResponseDto = {
        RecordMatchResponseDto("m1")
    },
    var matchDetail: MatchDetailDto? = null,
) : PlayboardApi {
    override suspend fun getAppUpdate(): com.org.playboard.data.remote.dto.AppUpdateDto = error("not used in this test")
    override suspend fun downloadApk(url: String): okhttp3.ResponseBody = error("not used in this test")
    var lastRecordRequest: RecordMatchRequestDto? = null
    var lastEditRequest: RecordMatchRequestDto? = null
    var lastEditMatchId: String? = null

    override suspend fun signInWithGoogle(request: GoogleSignInRequestDto): TokenResponseDto =
        error("not used in this test")

    override suspend fun refresh(request: RefreshRequestDto): TokenResponseDto = error("not used in this test")
    override suspend fun getGroups(): GroupsResponseDto = GroupsResponseDto(groups)
    override suspend fun createGroup(request: CreateGroupRequestDto): GroupDto = error("not used in this test")
    override suspend fun joinGroup(request: JoinGroupRequestDto): GroupDto = error("not used in this test")
    override suspend fun renameGroup(groupId: String, request: RenameGroupRequestDto): GroupDto = error("not used in this test")
    override suspend fun createInvite(groupId: String, request: CreateInviteRequestDto): InviteResponseDto =
        error("not used in this test")
    override suspend fun getLeaderboard(groupId: String, from: String?, to: String?): LeaderboardResponseDto = error("not used in this test")
    override suspend fun registerDevice(request: com.org.playboard.data.remote.dto.RegisterDeviceRequestDto) = error("not used in this test")
    override suspend fun unregisterDevice(request: com.org.playboard.data.remote.dto.UnregisterDeviceRequestDto) = error("not used in this test")
    override suspend fun getMembers(groupId: String): MembersResponseDto = MembersResponseDto(members)
    override suspend fun getGroupTrophies(groupId: String, limit: Int): List<MonthlyTrophyDto> = emptyList()
    override suspend fun addMember(groupId: String, request: com.org.playboard.data.remote.dto.AddMemberRequestDto): com.org.playboard.data.remote.dto.MemberDto = error("not used in this test")
    override suspend fun removeMember(groupId: String, userId: String) = error("unused")
    override suspend fun changeMemberRole(groupId: String, userId: String, request: com.org.playboard.data.remote.dto.UpdateRoleRequestDto): com.org.playboard.data.remote.dto.MemberDto = error("unused")
    override suspend fun updateSession(groupId: String, request: com.org.playboard.data.remote.dto.UpdateSessionRequestDto): com.org.playboard.data.remote.dto.GroupDto = error("unused")
    override suspend fun getPlayerStats(groupId: String, userId: String): PlayerStatsDto = error("not used in this test")
    override suspend fun getPlayerAttendance(groupId: String, userId: String, from: String, to: String): com.org.playboard.data.remote.dto.PlayerAttendanceDto = com.org.playboard.data.remote.dto.PlayerAttendanceDto()
    override suspend fun recordMatch(groupId: String, request: RecordMatchRequestDto): RecordMatchResponseDto {
        lastRecordRequest = request
        return recordMatchBehavior(request)
    }
    override suspend fun getMatches(groupId: String, cursor: String?, limit: Int?, mine: Boolean?): MatchListResponseDto =
        error("not used in this test")
    override suspend fun getMatchDetail(groupId: String, matchId: String): MatchDetailDto =
        matchDetail ?: error("no match detail stubbed")
    override suspend fun editMatch(groupId: String, matchId: String, request: RecordMatchRequestDto): MatchDetailDto {
        lastEditMatchId = matchId
        lastEditRequest = request
        return matchDetail ?: error("no match detail stubbed")
    }
    override suspend fun updateDisplayName(request: com.org.playboard.data.remote.dto.UpdateUserRequestDto): com.org.playboard.data.remote.dto.UserSummaryDto = error("not used in this test")
    override suspend fun uploadUserPhoto(file: okhttp3.MultipartBody.Part): com.org.playboard.data.remote.dto.UserSummaryDto = error("not used in this test")
    override suspend fun updateAvatar(request: com.org.playboard.data.remote.dto.UpdateAvatarRequestDto): com.org.playboard.data.remote.dto.UserSummaryDto = error("not used in this test")
    override suspend fun deleteMatch(groupId: String, matchId: String) = error("not used in this test")
}

private fun groupDto(id: String, name: String) = GroupDto(
    id = id,
    name = name,
    avatarColor = "#C7EA2B",
    sportCode = "badminton_doubles",
    memberCount = 4,
    matchCount = 0,
    myRole = "owner",
)

private fun memberDto(id: String, name: String) = MemberDto(
    userId = id,
    displayName = name,
    photoUrl = null,
    avatarColor = "#FF3D8A",
    role = "member",
)

private fun matchInvalidScoresException(): HttpException {
    val body = """{"code":"MATCH_INVALID_SCORES"}""".toResponseBody("application/json".toMediaType())
    return HttpException(Response.error<Any>(422, body))
}

/** Fills both teams via the picker: u1,u2 → Team 1; u3,u4 → Team 2. */
private fun AddMatchViewModel.buildTeams() {
    onEmptySlotClicked(1); onPlayerPicked("u1")
    onEmptySlotClicked(1); onPlayerPicked("u2")
    onEmptySlotClicked(2); onPlayerPicked("u3")
    onEmptySlotClicked(2); onPlayerPicked("u4")
}

@OptIn(ExperimentalCoroutinesApi::class)
class AddMatchViewModelTest {

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

    private fun deps(api: FakePlayboardApi): Triple<AuthRepository, GroupRepository, MatchRepository> {
        val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(testDispatcher),
            produceFile = { tempFolder.newFile("test.preferences_pb") },
        )
        val json = Json { ignoreUnknownKeys = true }
        val auth = AuthRepository(api, TokenStore(dataStore), com.org.playboard.data.device.DeviceRegistrar(api))
        val groups = testGroupRepository(api, json)
        val matches = MatchRepository(api, groups, json)
        return Triple(auth, groups, matches)
    }

    /** Builds a VM with the active group already selected and its roster loaded. */
    private suspend fun readyViewModel(api: FakePlayboardApi): AddMatchViewModel {
        val (auth, groups, matches) = deps(api)
        groups.refreshGroups()
        val viewModel = AddMatchViewModel(auth, groups, matches)
        return viewModel
    }

    private val fourPlayers = listOf(
        memberDto("u1", "Raj"),
        memberDto("u2", "Dev"),
        memberDto("u3", "Marcus"),
        memberDto("u4", "Kiran"),
    )

    @Test
    fun `loads the active group's roster`() = runTest(testDispatcher) {
        val api = FakePlayboardApi(groups = listOf(groupDto("g1", "Smashers")), members = fourPlayers)
        val viewModel = readyViewModel(api)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals("g1", state.groupId)
        assertEquals(4, state.roster.size)
    }

    @Test
    fun `a member joining refreshes the roster in place without clearing the form`() = runTest(testDispatcher) {
        val api = FakePlayboardApi(groups = listOf(groupDto("g1", "Smashers")), members = fourPlayers)
        val (auth, groups, matches) = deps(api)
        groups.refreshGroups()
        val viewModel = AddMatchViewModel(auth, groups, matches)
        advanceUntilIdle()

        viewModel.buildTeams()
        assertTrue(viewModel.uiState.value.teamsComplete)

        // A new member joins the group; a foreground resync / recorded match bumps the revision.
        api.members = fourPlayers + memberDto("u5", "Sam")
        groups.notifyMatchesChanged()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(5, state.roster.size) // new member is now pickable
        assertTrue(state.roster.any { it.id == "u5" })
        // Teams the user was already building stay intact.
        assertEquals(listOf("u1", "u2"), state.team1)
        assertEquals(listOf("u3", "u4"), state.team2)
    }

    @Test
    fun `assigning players fills team 1 then team 2 and ignores a fifth`() = runTest(testDispatcher) {
        val fivePlayers = fourPlayers + memberDto("u5", "Sam")
        val api = FakePlayboardApi(groups = listOf(groupDto("g1", "Smashers")), members = fivePlayers)
        val viewModel = readyViewModel(api)
        advanceUntilIdle()

        viewModel.buildTeams()
        var state = viewModel.uiState.value
        assertEquals(listOf("u1", "u2"), state.team1)
        assertEquals(listOf("u3", "u4"), state.team2)
        assertTrue(state.teamsComplete)
        // The 5th player is no longer available to pick.
        assertTrue(state.availablePlayers.map { it.id }.contains("u5"))

        // Team 1 is full — opening the picker for it is a no-op, so a pick can't land there.
        viewModel.onEmptySlotClicked(1)
        assertNull(viewModel.uiState.value.playerPickerTeam)
        viewModel.onPlayerPicked("u5")
        state = viewModel.uiState.value
        assertEquals(listOf("u1", "u2"), state.team1)
        assertEquals(listOf("u3", "u4"), state.team2)

        // Removing a player frees the slot and returns them to the available list.
        viewModel.onRemovePlayer("u1")
        assertEquals(listOf("u2"), viewModel.uiState.value.team1)
        assertTrue(viewModel.uiState.value.availablePlayers.map { it.id }.contains("u1"))
    }

    @Test
    fun `guest fillers collapse to a single available entry`() {
        // A real member plus the group's 3 interchangeable guest fillers.
        val roster = listOf(
            Member("u1", "Sriram", null, null, "#9ADE28", "owner"),
            Member("g1", "Guest 1", null, null, "#9AA0A6", "guest"),
            Member("g2", "Guest 2", null, null, "#9AA0A6", "guest"),
            Member("g3", "Guest 3", null, null, "#9AA0A6", "guest"),
        )
        val fresh = AddMatchUiState(roster = roster)

        // Nobody assigned: exactly one guest entry (the first), after the real member.
        assertEquals(listOf("u1", "g1"), fresh.availablePlayers.map { it.id })

        // Picking the guest consumes one distinct id; the next guest becomes the entry.
        val oneUsed = fresh.copy(team1 = listOf("g1"))
        assertEquals(listOf("u1", "g2"), oneUsed.availablePlayers.map { it.id })

        // All three guests placed: no guest entry remains in the picker.
        val allUsed = fresh.copy(team1 = listOf("g1", "g2"), team2 = listOf("g3"))
        assertEquals(listOf("u1"), allUsed.availablePlayers.map { it.id })
        assertTrue(allUsed.availablePlayers.none { it.isGuest })

        // Removing a placed guest (g2 freed) brings a single guest entry back.
        val afterRemoval = allUsed.copy(team1 = listOf("g1"))
        assertEquals(1, afterRemoval.availablePlayers.count { it.isGuest })
        assertEquals("g2", afterRemoval.availablePlayers.first { it.isGuest }.id)
    }

    @Test
    fun `slotLabel hides guest numbering but keeps real names`() {
        assertEquals("Guest", Member("g1", "Guest 1", null, null, "#9AA0A6", "guest").slotLabel())
        assertEquals("Sriram", Member("u1", "Sriram", null, null, "#9ADE28", "owner").slotLabel())
    }

    @Test
    fun `winner is auto-derived from sets and gates recording`() = runTest(testDispatcher) {
        val api = FakePlayboardApi(groups = listOf(groupDto("g1", "Smashers")), members = fourPlayers)
        val viewModel = readyViewModel(api)
        advanceUntilIdle()

        viewModel.buildTeams()
        assertFalse(viewModel.uiState.value.canRecord) // no scores yet

        viewModel.onSetScoreChanged(0, 1, "21")
        viewModel.onSetScoreChanged(0, 2, "12")
        viewModel.onAddSet()
        viewModel.onSetScoreChanged(1, 1, "21")
        viewModel.onSetScoreChanged(1, 2, "17")

        val state = viewModel.uiState.value
        assertEquals(1, state.autoWinner)
        assertEquals(1, state.effectiveWinner)
        assertTrue(state.canRecord)
    }

    @Test
    fun `a tied set blocks recording`() = runTest(testDispatcher) {
        val api = FakePlayboardApi(groups = listOf(groupDto("g1", "Smashers")), members = fourPlayers)
        val viewModel = readyViewModel(api)
        advanceUntilIdle()

        viewModel.buildTeams()
        viewModel.onSetScoreChanged(0, 1, "21")
        viewModel.onSetScoreChanged(0, 2, "21")

        assertNull(viewModel.uiState.value.autoWinner)
        assertFalse(viewModel.uiState.value.canRecord)
    }

    @Test
    fun `recording a valid match sends the request, emits recorded, and resets the form`() =
        runTest(testDispatcher) {
            val api = FakePlayboardApi(groups = listOf(groupDto("g1", "Smashers")), members = fourPlayers)
            val viewModel = readyViewModel(api)
            val recorded = mutableListOf<Unit>()
            // Collect on an unconfined test dispatcher so it reacts to emits eagerly.
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.recorded.collect { recorded.add(Unit) }
            }
            advanceUntilIdle() // let the roster load

            viewModel.buildTeams()
            viewModel.onSetScoreChanged(0, 1, "21")
            viewModel.onSetScoreChanged(0, 2, "12")
            viewModel.onRecord()
            advanceUntilIdle()

            val request = api.lastRecordRequest!!
            assertEquals(listOf("u1", "u2"), request.teams[0].playerIds)
            assertEquals(listOf("u3", "u4"), request.teams[1].playerIds)
            assertEquals("winningTeamNo", 1, request.winningTeamNo)

            // onSuccess ran: form reset, ready for the next entry.
            val state = viewModel.uiState.value
            assertTrue("team1 reset", state.team1.isEmpty())
            assertEquals("sets reset", 1, state.sets.size)
            assertFalse("not submitting", state.isSubmitting)

            assertEquals("recorded events", 1, recorded.size)
        }

    @Test
    fun `an invalid-scores rejection surfaces an inline error`() = runTest(testDispatcher) {
        val api = FakePlayboardApi(
            groups = listOf(groupDto("g1", "Smashers")),
            members = fourPlayers,
            recordMatchBehavior = { throw matchInvalidScoresException() },
        )
        val viewModel = readyViewModel(api)
        advanceUntilIdle()

        viewModel.buildTeams()
        viewModel.onSetScoreChanged(0, 1, "21")
        viewModel.onSetScoreChanged(0, 2, "12")
        viewModel.onRecord()
        advanceUntilIdle()

        assertEquals(RecordMatchError.INVALID_SCORES, viewModel.uiState.value.submitError)
        assertFalse(viewModel.uiState.value.isSubmitting)
    }

    @Test
    fun `entering edit mode pre-fills teams, sets, and winner from the match`() = runTest(testDispatcher) {
        val api = FakePlayboardApi(
            groups = listOf(groupDto("g1", "Smashers")),
            members = fourPlayers,
            matchDetail = detailDto(winningTeamNo = 2),
        )
        val viewModel = readyViewModel(api)
        advanceUntilIdle()

        viewModel.onModeRequested("m1")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue("editing", state.isEditing)
        assertEquals("m1", state.editingMatchId)
        assertEquals(listOf("u1", "u2"), state.team1)
        assertEquals(listOf("u3", "u4"), state.team2)
        assertEquals(listOf("21", "21"), state.sets.map { it.team1 })
        assertEquals(listOf("15", "18"), state.sets.map { it.team2 })
        assertEquals(2, state.winnerOverride)
        assertTrue("can save", state.canRecord)
    }

    @Test
    fun `saving an edit sends a PATCH for that match, emits recorded, and resets to create mode`() =
        runTest(testDispatcher) {
            val api = FakePlayboardApi(
                groups = listOf(groupDto("g1", "Smashers")),
                members = fourPlayers,
                matchDetail = detailDto(winningTeamNo = 2),
            )
            val viewModel = readyViewModel(api)
            val recorded = mutableListOf<Unit>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.recorded.collect { recorded.add(Unit) }
            }
            advanceUntilIdle()

            viewModel.onModeRequested("m1")
            advanceUntilIdle()
            viewModel.onRecord()
            advanceUntilIdle()

            assertEquals("edited match id", "m1", api.lastEditMatchId)
            val request = api.lastEditRequest!!
            assertEquals(listOf("u1", "u2"), request.teams[0].playerIds)
            assertEquals(listOf("u3", "u4"), request.teams[1].playerIds)
            assertEquals("winningTeamNo", 2, request.winningTeamNo)
            assertNull("create path untouched", api.lastRecordRequest)

            // Back to a fresh create form after a successful save.
            val state = viewModel.uiState.value
            assertFalse("no longer editing", state.isEditing)
            assertTrue("team1 reset", state.team1.isEmpty())
            assertEquals("recorded events", 1, recorded.size)
        }

    @Test
    fun `saving an edit keeps the original played date`() = runTest(testDispatcher) {
        // Regression: editMatch used to reuse the create path's request builder, which
        // stamps playedAt = now(). That re-dated every edited match to the edit time and
        // moved it under today's heading in the Matches log.
        val api = FakePlayboardApi(
            groups = listOf(groupDto("g1", "Smashers")),
            members = fourPlayers,
            matchDetail = detailDto(winningTeamNo = 2),
        )
        val viewModel = readyViewModel(api)
        advanceUntilIdle()

        viewModel.onModeRequested("m1")
        advanceUntilIdle()
        viewModel.onRecord()
        advanceUntilIdle()

        assertEquals("played date round-trips", "2026-07-09T06:58:00Z", api.lastEditRequest!!.playedAt)
    }

    /** A two-set match: u1/u2 (team 1) vs u3/u4 (team 2), with [winningTeamNo] marked the winner. */
    private fun detailDto(winningTeamNo: Int) = MatchDetailDto(
        id = "m1",
        playedAt = "2026-07-09T06:58:00Z",
        teams = listOf(
            MatchTeamDto(
                teamNo = 1,
                isWinner = winningTeamNo == 1,
                players = listOf(
                    MatchPlayerDto("u1", "Raj", "#9ADE28"),
                    MatchPlayerDto("u2", "Dev", "#3DB4FF"),
                ),
            ),
            MatchTeamDto(
                teamNo = 2,
                isWinner = winningTeamNo == 2,
                players = listOf(
                    MatchPlayerDto("u3", "Marcus", "#FF8A3D"),
                    MatchPlayerDto("u4", "Kiran", "#EAC72B"),
                ),
            ),
        ),
        sets = listOf(MatchSetDto(1, 21, 15), MatchSetDto(2, 21, 18)),
        recordedBy = RecordedByDto("u1", "Raj"),
        recordedAt = "2026-07-09T06:58:00Z",
        events = emptyList(),
    )
}
