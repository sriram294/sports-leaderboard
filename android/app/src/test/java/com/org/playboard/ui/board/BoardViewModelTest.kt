package com.org.playboard.ui.board

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.org.playboard.data.auth.AuthRepository
import com.org.playboard.data.auth.TokenStore
import com.org.playboard.data.device.DeviceRegistrar
import com.org.playboard.data.group.GroupRepository
import com.org.playboard.testing.testGroupRepository
import com.org.playboard.data.leaderboard.LeaderboardRepository
import com.org.playboard.data.remote.PlayboardApi
import com.org.playboard.data.remote.dto.CreateGroupRequestDto
import com.org.playboard.data.remote.dto.CreateInviteRequestDto
import com.org.playboard.data.remote.dto.GoogleSignInRequestDto
import com.org.playboard.data.remote.dto.GroupDto
import com.org.playboard.data.remote.dto.GroupsResponseDto
import com.org.playboard.data.remote.dto.InviteResponseDto
import com.org.playboard.data.remote.dto.JoinGroupRequestDto
import com.org.playboard.data.remote.dto.RenameGroupRequestDto
import com.org.playboard.data.remote.dto.LeaderboardEntryDto
import com.org.playboard.data.remote.dto.LeaderboardResponseDto
import com.org.playboard.data.remote.dto.MatchDetailDto
import com.org.playboard.data.remote.dto.MatchListResponseDto
import com.org.playboard.data.remote.dto.MatchPlayerDto
import com.org.playboard.data.remote.dto.MatchSetDto
import com.org.playboard.data.remote.dto.MatchSummaryDto
import com.org.playboard.data.remote.dto.MatchTeamDto
import com.org.playboard.data.remote.dto.MembersResponseDto
import com.org.playboard.data.remote.dto.PlayerStatsDto
import com.org.playboard.data.remote.dto.RecordMatchRequestDto
import com.org.playboard.data.remote.dto.RecordMatchResponseDto
import com.org.playboard.data.remote.dto.RefreshRequestDto
import com.org.playboard.data.remote.dto.TokenResponseDto
import com.org.playboard.data.remote.dto.UserSummaryDto
import com.org.playboard.data.stats.StatsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

private class FakePlayboardApi(
    var groupsResult: suspend () -> GroupsResponseDto = { GroupsResponseDto(emptyList()) },
    var leaderboardResult: suspend (String) -> LeaderboardResponseDto = { LeaderboardResponseDto(emptyList()) },
    // Defaults to throwing: StatsRepository wraps it in runCatching, so the form
    // degrades silently and tests that don't care about form are unaffected.
    var statsResult: suspend (String, String) -> PlayerStatsDto = { _, _ -> error("no stats in this test") },
) : PlayboardApi {
    override suspend fun getAppUpdate(): com.org.playboard.data.remote.dto.AppUpdateDto = error("not used in this test")
    override suspend fun downloadApk(url: String): okhttp3.ResponseBody = error("not used in this test")
    override suspend fun signInWithGoogle(request: GoogleSignInRequestDto): TokenResponseDto =
        TokenResponseDto("access", "refresh", 900, UserSummaryDto("u1", "Raj", "raj@example.com", null, "#9ADE28"))

    override suspend fun refresh(request: RefreshRequestDto): TokenResponseDto = error("not used in this test")
    override suspend fun getGroups(): GroupsResponseDto = groupsResult()
    override suspend fun createGroup(request: CreateGroupRequestDto): GroupDto = error("not used in this test")
    override suspend fun joinGroup(request: JoinGroupRequestDto): GroupDto = error("not used in this test")
    override suspend fun renameGroup(groupId: String, request: RenameGroupRequestDto): GroupDto = error("not used in this test")
    override suspend fun createInvite(groupId: String, request: CreateInviteRequestDto): InviteResponseDto =
        error("not used in this test")
    /** The window params of the most recent leaderboard fetch, for range-scoping assertions. */
    var lastFrom: String? = null
    var lastTo: String? = null
    override suspend fun getLeaderboard(groupId: String, from: String?, to: String?): LeaderboardResponseDto {
        lastFrom = from
        lastTo = to
        return leaderboardResult(groupId)
    }
    override suspend fun registerDevice(request: com.org.playboard.data.remote.dto.RegisterDeviceRequestDto) = error("not used in this test")
    override suspend fun unregisterDevice(request: com.org.playboard.data.remote.dto.UnregisterDeviceRequestDto) = error("not used in this test")
    override suspend fun getMembers(groupId: String): MembersResponseDto = MembersResponseDto(emptyList())
    override suspend fun addMember(groupId: String, request: com.org.playboard.data.remote.dto.AddMemberRequestDto): com.org.playboard.data.remote.dto.MemberDto = error("not used in this test")
    override suspend fun getPlayerStats(groupId: String, userId: String): PlayerStatsDto = statsResult(groupId, userId)
    override suspend fun recordMatch(groupId: String, request: RecordMatchRequestDto): RecordMatchResponseDto =
        error("recordMatch not used in this test")
    override suspend fun getMatches(groupId: String, cursor: String?, limit: Int?): MatchListResponseDto =
        error("not used in this test")
    override suspend fun getMatchDetail(groupId: String, matchId: String): MatchDetailDto =
        error("not used in this test")
    override suspend fun editMatch(groupId: String, matchId: String, request: RecordMatchRequestDto): MatchDetailDto =
        error("not used in this test")
    override suspend fun updateDisplayName(request: com.org.playboard.data.remote.dto.UpdateUserRequestDto): com.org.playboard.data.remote.dto.UserSummaryDto = error("not used in this test")
    override suspend fun uploadUserPhoto(file: okhttp3.MultipartBody.Part): com.org.playboard.data.remote.dto.UserSummaryDto = error("not used in this test")
    override suspend fun deleteMatch(groupId: String, matchId: String) = error("not used in this test")
}

private fun groupDto(id: String, name: String, myRole: String = "member") = GroupDto(
    id = id,
    name = name,
    avatarColor = "#C7EA2B",
    sportCode = "badminton_doubles",
    memberCount = 6,
    matchCount = 10,
    myRole = myRole,
)

private fun entryDto(
    rank: Int,
    name: String,
    gamesPlayed: Int,
    wins: Int,
    pointsFor: Int,
    winRate: Double,
    pointsAgainst: Int = 0,
) = LeaderboardEntryDto(
    rank = rank,
    userId = "user-$name",
    displayName = name,
    photoUrl = null,
    avatarColor = "#FF3D8A",
    gamesPlayed = gamesPlayed,
    wins = wins,
    losses = gamesPlayed - wins,
    pointsFor = pointsFor,
    pointsAgainst = pointsAgainst,
    winRate = winRate,
)

/**
 * A [PlayerStatsDto] whose `recentMatches` yield exactly [results] as the signed-in
 * user's ("u1") form, newest first — each entry puts u1 on the winning or losing team.
 */
private fun statsWithForm(results: List<Boolean>, userId: String = "u1") = PlayerStatsDto(
    userId = userId,
    displayName = "Raj",
    photoUrl = null,
    avatarColor = "#9ADE28",
    matchesPlayed = results.size,
    wins = results.count { it },
    losses = results.count { !it },
    pointsFor = 0,
    pointsAgainst = 0,
    winRate = 0.0,
    currentStreak = 0,
    bestStreak = 0,
    bestPartner = null,
    recentMatches = results.mapIndexed { i, win ->
        MatchSummaryDto(
            id = "m$i",
            playedAt = "2026-07-0${i + 1}T06:00:00Z",
            teams = listOf(
                MatchTeamDto(1, win, listOf(MatchPlayerDto(userId, "Raj", "#9ADE28", null), MatchPlayerDto("u2", "Dev", "#3DB4FF", null))),
                MatchTeamDto(2, !win, listOf(MatchPlayerDto("u3", "Marcus", "#FF8A3D", null), MatchPlayerDto("u4", "Kiran", "#EAC72B", null))),
            ),
            sets = listOf(MatchSetDto(1, 21, 15)),
        )
    },
)

@OptIn(ExperimentalCoroutinesApi::class)
class BoardViewModelTest {

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

    private fun repo(api: FakePlayboardApi) = testGroupRepository(api)

    /** Builds the ViewModel with a signed-in "u1" session so the form collector has a user. */
    private suspend fun viewModel(repo: GroupRepository, api: FakePlayboardApi): BoardViewModel {
        val dataStore = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(testDispatcher),
            produceFile = { tempFolder.newFile("ds-${System.nanoTime()}.preferences_pb") },
        )
        val tokenStore = TokenStore(dataStore)
        val auth = AuthRepository(api, tokenStore, DeviceRegistrar(api))
        auth.signInWithGoogle("token")
        return BoardViewModel(repo, LeaderboardRepository(api), auth, StatsRepository(api))
    }

    @Test
    fun `loads the active group's leaderboard`() = runTest(testDispatcher) {
        val api = FakePlayboardApi(
            groupsResult = { GroupsResponseDto(listOf(groupDto("g1", "Saturday Smashers"), groupDto("g2", "Office League"))) },
            leaderboardResult = { groupId ->
                assertEquals("g1", groupId)
                LeaderboardResponseDto(listOf(entryDto(1, "Priya", 6, 6, 252, 1.0)))
            },
        )
        val repo = repo(api)
        val viewModel = viewModel(repo, api)
        repo.refreshGroups() // the shared switcher owns loading the group list
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertFalse(state.hasLoadFailed)
        assertEquals("g1", state.selectedGroup?.id)
        assertEquals(listOf("Priya"), state.rankings.map { it.displayName })
    }

    @Test
    fun `switching the active group reloads the leaderboard`() = runTest(testDispatcher) {
        val api = FakePlayboardApi(
            groupsResult = { GroupsResponseDto(listOf(groupDto("g1", "Saturday Smashers"), groupDto("g2", "Office League"))) },
            leaderboardResult = { groupId ->
                val name = if (groupId == "g1") "Priya" else "Dev"
                LeaderboardResponseDto(listOf(entryDto(1, name, 6, 5, 245, 0.83)))
            },
        )
        val repo = repo(api)
        val viewModel = viewModel(repo, api)
        repo.refreshGroups()
        advanceUntilIdle()

        // The shared switcher changes the active group via the repository.
        repo.selectGroup("g2")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("g2", state.selectedGroup?.id)
        assertEquals(listOf("Dev"), state.rankings.map { it.displayName })
    }

    @Test
    fun `group-list fetch failure sets the error flag and retry recovers`() = runTest(testDispatcher) {
        var failing = true
        val api = FakePlayboardApi(
            groupsResult = {
                if (failing) throw RuntimeException("network down")
                GroupsResponseDto(listOf(groupDto("g1", "Saturday Smashers")))
            },
            leaderboardResult = { LeaderboardResponseDto(listOf(entryDto(1, "Priya", 6, 6, 252, 1.0))) },
        )
        val repo = repo(api)
        val viewModel = viewModel(repo, api)
        repo.refreshGroups()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.hasLoadFailed)
        assertFalse(viewModel.uiState.value.isLoading)

        failing = false
        viewModel.refresh() // no active group → retries the group-list fetch
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.hasLoadFailed)
        assertEquals("g1", state.selectedGroup?.id)
        assertEquals(1, state.rankings.size)
    }

    @Test
    fun `no groups yields the empty state without an error`() = runTest(testDispatcher) {
        val api = FakePlayboardApi()
        val repo = repo(api)
        val viewModel = viewModel(repo, api)
        repo.refreshGroups()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertFalse(state.hasLoadFailed)
        assertNull(state.selectedGroup)
        assertTrue(state.rankings.isEmpty())
    }

    @Test
    fun `table sorts by the selected column while podium keeps canonical order`() = runTest(testDispatcher) {
        val api = FakePlayboardApi(
            groupsResult = { GroupsResponseDto(listOf(groupDto("g1", "Saturday Smashers"))) },
            leaderboardResult = {
                LeaderboardResponseDto(
                    listOf(
                        // Diff order (Dev +145, Priya +72, Raj +5) deliberately differs from
                        // both canonical order and points-for order (Raj has the most points
                        // but the worst difference), so this pins the sort to the difference.
                        entryDto(1, "Priya", 6, 6, 252, 1.0, pointsAgainst = 180),
                        entryDto(2, "Dev", 6, 5, 245, 0.83, pointsAgainst = 100),
                        entryDto(3, "Raj", 8, 4, 315, 0.5, pointsAgainst = 310),
                    ),
                )
            },
        )
        val repo = repo(api)
        val viewModel = viewModel(repo, api)
        repo.refreshGroups()
        advanceUntilIdle()

        viewModel.onSortColumnSelected(RankingSortColumn.POINTS_DIFF)

        val state = viewModel.uiState.value
        assertEquals(listOf("Dev", "Priya", "Raj"), state.tableRows.map { it.displayName })
        assertEquals(listOf("Priya", "Dev", "Raj"), state.podium.map { it.displayName })
    }

    @Test
    fun `default range is this month and scopes the initial fetch to the month window`() = runTest(testDispatcher) {
        val api = FakePlayboardApi(
            groupsResult = { GroupsResponseDto(listOf(groupDto("g1", "Saturday Smashers"))) },
            leaderboardResult = { LeaderboardResponseDto(listOf(entryDto(1, "Priya", 6, 6, 252, 1.0))) },
        )
        val repo = repo(api)
        val viewModel = viewModel(repo, api)
        repo.refreshGroups()
        advanceUntilIdle()

        assertEquals(LeaderboardTimeRange.MONTH, viewModel.uiState.value.selectedTimeRange)
        val (from, to) = LeaderboardTimeRange.MONTH.window()!!
        assertEquals(from, api.lastFrom)
        assertEquals(to, api.lastTo)
    }

    @Test
    fun `selecting All Time fetches without a window`() = runTest(testDispatcher) {
        val api = FakePlayboardApi(
            groupsResult = { GroupsResponseDto(listOf(groupDto("g1", "Saturday Smashers"))) },
            leaderboardResult = { LeaderboardResponseDto(listOf(entryDto(1, "Priya", 6, 6, 252, 1.0))) },
        )
        val repo = repo(api)
        val viewModel = viewModel(repo, api)
        repo.refreshGroups()
        advanceUntilIdle()

        viewModel.onTimeRangeSelected(LeaderboardTimeRange.ALL_TIME)
        advanceUntilIdle()

        assertEquals(LeaderboardTimeRange.ALL_TIME, viewModel.uiState.value.selectedTimeRange)
        assertNull(api.lastFrom)
        assertNull(api.lastTo)
    }

    @Test
    fun `selecting This Week scopes the fetch to the week window`() = runTest(testDispatcher) {
        val api = FakePlayboardApi(
            groupsResult = { GroupsResponseDto(listOf(groupDto("g1", "Saturday Smashers"))) },
            leaderboardResult = { LeaderboardResponseDto(listOf(entryDto(1, "Priya", 6, 6, 252, 1.0))) },
        )
        val repo = repo(api)
        val viewModel = viewModel(repo, api)
        repo.refreshGroups()
        advanceUntilIdle()

        viewModel.onTimeRangeSelected(LeaderboardTimeRange.WEEK)
        advanceUntilIdle()

        val (from, to) = LeaderboardTimeRange.WEEK.window()!!
        assertNotNull(from)
        assertEquals(from, api.lastFrom)
        assertEquals(to, api.lastTo)
    }

    @Test
    fun `the selected range persists across a group switch`() = runTest(testDispatcher) {
        val api = FakePlayboardApi(
            groupsResult = { GroupsResponseDto(listOf(groupDto("g1", "Saturday Smashers"), groupDto("g2", "Office League"))) },
            leaderboardResult = { LeaderboardResponseDto(listOf(entryDto(1, "Priya", 6, 6, 252, 1.0))) },
        )
        val repo = repo(api)
        val viewModel = viewModel(repo, api)
        repo.refreshGroups()
        advanceUntilIdle()

        viewModel.onTimeRangeSelected(LeaderboardTimeRange.ALL_TIME)
        advanceUntilIdle()

        // Switching groups keeps the range: g2's reload is also all-time (no window).
        repo.selectGroup("g2")
        advanceUntilIdle()

        assertEquals(LeaderboardTimeRange.ALL_TIME, viewModel.uiState.value.selectedTimeRange)
        assertEquals("g2", viewModel.uiState.value.selectedGroup?.id)
        assertNull(api.lastFrom)
        assertNull(api.lastTo)
    }

    @Test
    fun `pull-to-refresh reloads the leaderboard and clears the refreshing flag`() = runTest(testDispatcher) {
        var players = listOf("Priya")
        val api = FakePlayboardApi(
            groupsResult = { GroupsResponseDto(listOf(groupDto("g1", "Saturday Smashers"))) },
            leaderboardResult = {
                LeaderboardResponseDto(players.mapIndexed { i, name -> entryDto(i + 1, name, 6, 6, 252, 1.0) })
            },
        )
        val repo = repo(api)
        val viewModel = viewModel(repo, api)
        repo.refreshGroups()
        advanceUntilIdle()
        assertEquals(listOf("Priya"), viewModel.uiState.value.rankings.map { it.displayName })

        // A new player joined + played since the initial load; pulling picks them up.
        players = listOf("Priya", "Newbie")
        viewModel.onPullRefresh()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(listOf("Priya", "Newbie"), state.rankings.map { it.displayName })
        assertFalse(state.isRefreshing)
    }

    @Test
    fun `form bar shows the signed-in user's last results newest first`() = runTest(testDispatcher) {
        val api = FakePlayboardApi(
            groupsResult = { GroupsResponseDto(listOf(groupDto("g1", "Saturday Smashers"))) },
            leaderboardResult = { LeaderboardResponseDto(listOf(entryDto(1, "Priya", 6, 6, 252, 1.0))) },
            statsResult = { _, _ -> statsWithForm(listOf(true, false, true, true, false)) },
        )
        val repo = repo(api)
        val viewModel = viewModel(repo, api)
        repo.refreshGroups()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(listOf(true, false, true, true, false), state.recentForm)
        assertTrue(state.showFormBar)
    }

    @Test
    fun `a player with no matches hides the form bar`() = runTest(testDispatcher) {
        val api = FakePlayboardApi(
            groupsResult = { GroupsResponseDto(listOf(groupDto("g1", "Saturday Smashers"))) },
            leaderboardResult = { LeaderboardResponseDto(listOf(entryDto(1, "Priya", 6, 6, 252, 1.0))) },
            statsResult = { _, _ -> statsWithForm(emptyList()) },
        )
        val repo = repo(api)
        val viewModel = viewModel(repo, api)
        repo.refreshGroups()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.recentForm.isEmpty())
        assertFalse(state.showFormBar)
    }

    @Test
    fun `a form fetch failure still renders the leaderboard`() = runTest(testDispatcher) {
        // statsResult defaults to throwing — the form must degrade silently.
        val api = FakePlayboardApi(
            groupsResult = { GroupsResponseDto(listOf(groupDto("g1", "Saturday Smashers"))) },
            leaderboardResult = { LeaderboardResponseDto(listOf(entryDto(1, "Priya", 6, 6, 252, 1.0))) },
        )
        val repo = repo(api)
        val viewModel = viewModel(repo, api)
        repo.refreshGroups()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(listOf("Priya"), state.rankings.map { it.displayName })
        assertFalse(state.hasLoadFailed)
        assertTrue(state.recentForm.isEmpty())
        assertFalse(state.showFormBar)
    }

    @Test
    fun `switching the active group reloads the form`() = runTest(testDispatcher) {
        val api = FakePlayboardApi(
            groupsResult = { GroupsResponseDto(listOf(groupDto("g1", "Saturday Smashers"), groupDto("g2", "Office League"))) },
            leaderboardResult = { LeaderboardResponseDto(listOf(entryDto(1, "Priya", 6, 6, 252, 1.0))) },
            statsResult = { groupId, _ ->
                if (groupId == "g1") statsWithForm(listOf(true, true)) else statsWithForm(listOf(false))
            },
        )
        val repo = repo(api)
        val viewModel = viewModel(repo, api)
        repo.refreshGroups()
        advanceUntilIdle()
        assertEquals(listOf(true, true), viewModel.uiState.value.recentForm)

        repo.selectGroup("g2")
        advanceUntilIdle()
        assertEquals(listOf(false), viewModel.uiState.value.recentForm)
    }

    @Test
    fun `switching group cancels an in-flight form fetch and loads the new group's now`() = runTest(testDispatcher) {
        val api = FakePlayboardApi(
            groupsResult = { GroupsResponseDto(listOf(groupDto("g1", "Saturday Smashers"), groupDto("g2", "Office League"))) },
            leaderboardResult = { LeaderboardResponseDto(listOf(entryDto(1, "Priya", 6, 6, 252, 1.0))) },
            // g1's form fetch hangs; g2's is immediate.
            statsResult = { groupId, _ ->
                if (groupId == "g1") {
                    delay(10_000)
                    statsWithForm(listOf(true, true))
                } else {
                    statsWithForm(listOf(false))
                }
            },
        )
        val repo = repo(api)
        val viewModel = viewModel(repo, api)
        repo.refreshGroups()
        // g1's form fetch is now in flight (suspended); switch before it could return.
        advanceTimeBy(1_000)
        repo.selectGroup("g2")
        // Advance only a little — still well within g1's 10s hang. collectLatest must have
        // cancelled g1's fetch and loaded g2's immediately; plain collect would stay blocked
        // on g1 and leave the form empty until t=10s.
        advanceTimeBy(1_000)
        assertEquals(listOf(false), viewModel.uiState.value.recentForm)

        // Drain the (cancelled) g1 timer so runTest ends cleanly; the stale result never lands.
        advanceUntilIdle()
        assertEquals(listOf(false), viewModel.uiState.value.recentForm)
    }

    @Test
    fun `pull-to-refresh reloads the form`() = runTest(testDispatcher) {
        var form = listOf(true, false)
        val api = FakePlayboardApi(
            groupsResult = { GroupsResponseDto(listOf(groupDto("g1", "Saturday Smashers"))) },
            leaderboardResult = { LeaderboardResponseDto(listOf(entryDto(1, "Priya", 6, 6, 252, 1.0))) },
            statsResult = { _, _ -> statsWithForm(form) },
        )
        val repo = repo(api)
        val viewModel = viewModel(repo, api)
        repo.refreshGroups()
        advanceUntilIdle()
        assertEquals(listOf(true, false), viewModel.uiState.value.recentForm)

        // A match played since the initial load; pulling picks up the new form.
        form = listOf(false, false, true)
        viewModel.onPullRefresh()
        advanceUntilIdle()
        assertEquals(listOf(false, false, true), viewModel.uiState.value.recentForm)
    }
}
