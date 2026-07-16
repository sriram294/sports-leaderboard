package com.org.playboard.ui.board

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
import com.org.playboard.data.remote.dto.MembersResponseDto
import com.org.playboard.data.remote.dto.PlayerStatsDto
import com.org.playboard.data.remote.dto.RecordMatchRequestDto
import com.org.playboard.data.remote.dto.RecordMatchResponseDto
import com.org.playboard.data.remote.dto.RefreshRequestDto
import com.org.playboard.data.remote.dto.TokenResponseDto
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
import org.junit.Test

private class FakePlayboardApi(
    var groupsResult: suspend () -> GroupsResponseDto = { GroupsResponseDto(emptyList()) },
    var leaderboardResult: suspend (String) -> LeaderboardResponseDto = { LeaderboardResponseDto(emptyList()) },
) : PlayboardApi {
    override suspend fun getAppUpdate(): com.org.playboard.data.remote.dto.AppUpdateDto = error("not used in this test")
    override suspend fun downloadApk(url: String): okhttp3.ResponseBody = error("not used in this test")
    override suspend fun signInWithGoogle(request: GoogleSignInRequestDto): TokenResponseDto =
        error("not used in this test")

    override suspend fun refresh(request: RefreshRequestDto): TokenResponseDto = error("not used in this test")
    override suspend fun getGroups(): GroupsResponseDto = groupsResult()
    override suspend fun createGroup(request: CreateGroupRequestDto): GroupDto = error("not used in this test")
    override suspend fun joinGroup(request: JoinGroupRequestDto): GroupDto = error("not used in this test")
    override suspend fun renameGroup(groupId: String, request: RenameGroupRequestDto): GroupDto = error("not used in this test")
    override suspend fun createInvite(groupId: String, request: CreateInviteRequestDto): InviteResponseDto =
        error("not used in this test")
    override suspend fun getLeaderboard(groupId: String): LeaderboardResponseDto = leaderboardResult(groupId)
    override suspend fun registerDevice(request: com.org.playboard.data.remote.dto.RegisterDeviceRequestDto) = error("not used in this test")
    override suspend fun unregisterDevice(request: com.org.playboard.data.remote.dto.UnregisterDeviceRequestDto) = error("not used in this test")
    override suspend fun getMembers(groupId: String): MembersResponseDto = MembersResponseDto(emptyList())
    override suspend fun addMember(groupId: String, request: com.org.playboard.data.remote.dto.AddMemberRequestDto): com.org.playboard.data.remote.dto.MemberDto = error("not used in this test")
    override suspend fun getPlayerStats(groupId: String, userId: String): PlayerStatsDto = error("not used in this test")
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

@OptIn(ExperimentalCoroutinesApi::class)
class BoardViewModelTest {

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

    private fun viewModel(repo: GroupRepository, api: FakePlayboardApi) =
        BoardViewModel(repo, LeaderboardRepository(api))

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
}
