package com.org.playboard.ui.add

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.org.playboard.data.auth.AuthRepository
import com.org.playboard.data.auth.TokenStore
import com.org.playboard.data.group.GroupRepository
import com.org.playboard.data.match.MatchRepository
import com.org.playboard.data.remote.PlayboardApi
import com.org.playboard.data.remote.dto.CreateGroupRequestDto
import com.org.playboard.data.remote.dto.CreateInviteRequestDto
import com.org.playboard.data.remote.dto.GoogleSignInRequestDto
import com.org.playboard.data.remote.dto.GroupDto
import com.org.playboard.data.remote.dto.GroupsResponseDto
import com.org.playboard.data.remote.dto.InviteResponseDto
import com.org.playboard.data.remote.dto.JoinGroupRequestDto
import com.org.playboard.data.remote.dto.LeaderboardResponseDto
import com.org.playboard.data.remote.dto.MatchDetailDto
import com.org.playboard.data.remote.dto.MatchListResponseDto
import com.org.playboard.data.remote.dto.MemberDto
import com.org.playboard.data.remote.dto.MembersResponseDto
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
) : PlayboardApi {
    var lastRecordRequest: RecordMatchRequestDto? = null

    override suspend fun signInWithGoogle(request: GoogleSignInRequestDto): TokenResponseDto =
        error("not used in this test")

    override suspend fun refresh(request: RefreshRequestDto): TokenResponseDto = error("not used in this test")
    override suspend fun getGroups(): GroupsResponseDto = GroupsResponseDto(groups)
    override suspend fun createGroup(request: CreateGroupRequestDto): GroupDto = error("not used in this test")
    override suspend fun joinGroup(request: JoinGroupRequestDto): GroupDto = error("not used in this test")
    override suspend fun createInvite(groupId: String, request: CreateInviteRequestDto): InviteResponseDto =
        error("not used in this test")
    override suspend fun getLeaderboard(groupId: String): LeaderboardResponseDto = error("not used in this test")
    override suspend fun getMembers(groupId: String): MembersResponseDto = MembersResponseDto(members)
    override suspend fun getPlayerStats(groupId: String, userId: String): PlayerStatsDto = error("not used in this test")
    override suspend fun recordMatch(groupId: String, request: RecordMatchRequestDto): RecordMatchResponseDto {
        lastRecordRequest = request
        return recordMatchBehavior(request)
    }
    override suspend fun getMatches(groupId: String, cursor: String?, limit: Int?): MatchListResponseDto =
        error("not used in this test")
    override suspend fun getMatchDetail(groupId: String, matchId: String): MatchDetailDto =
        error("not used in this test")
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
        val auth = AuthRepository(api, TokenStore(dataStore))
        val groups = GroupRepository(api, json)
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
}
