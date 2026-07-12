package com.org.playboard.ui.stats

import com.org.playboard.data.leaderboard.LeaderboardRepository
import com.org.playboard.data.match.MatchRepository
import com.org.playboard.data.remote.PlayboardApi
import com.org.playboard.data.remote.dto.CreateGroupRequestDto
import com.org.playboard.data.remote.dto.CreateInviteRequestDto
import com.org.playboard.data.remote.dto.GoogleSignInRequestDto
import com.org.playboard.data.remote.dto.GroupDto
import com.org.playboard.data.remote.dto.GroupsResponseDto
import com.org.playboard.data.remote.dto.InviteResponseDto
import com.org.playboard.data.remote.dto.JoinGroupRequestDto
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
import com.org.playboard.data.remote.dto.RenameGroupRequestDto
import com.org.playboard.data.remote.dto.TokenResponseDto
import com.org.playboard.testing.testGroupRepository
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private class FakePlayboardApi(
    var groupsResult: suspend () -> GroupsResponseDto = { GroupsResponseDto(emptyList()) },
    var leaderboardResult: suspend (String) -> LeaderboardResponseDto = { LeaderboardResponseDto(emptyList()) },
    var matchesResult: suspend (String) -> MatchListResponseDto = { MatchListResponseDto(emptyList(), null) },
) : PlayboardApi {
    override suspend fun signInWithGoogle(request: GoogleSignInRequestDto): TokenResponseDto = error("unused")
    override suspend fun refresh(request: RefreshRequestDto): TokenResponseDto = error("unused")
    override suspend fun getGroups(): GroupsResponseDto = groupsResult()
    override suspend fun createGroup(request: CreateGroupRequestDto): GroupDto = error("unused")
    override suspend fun joinGroup(request: JoinGroupRequestDto): GroupDto = error("unused")
    override suspend fun renameGroup(groupId: String, request: RenameGroupRequestDto): GroupDto = error("unused")
    override suspend fun createInvite(groupId: String, request: CreateInviteRequestDto): InviteResponseDto = error("unused")
    override suspend fun getLeaderboard(groupId: String): LeaderboardResponseDto = leaderboardResult(groupId)
    override suspend fun getMembers(groupId: String): MembersResponseDto = error("unused")
    override suspend fun getPlayerStats(groupId: String, userId: String): PlayerStatsDto = error("unused")
    override suspend fun recordMatch(groupId: String, request: RecordMatchRequestDto): RecordMatchResponseDto = error("unused")
    override suspend fun getMatches(groupId: String, cursor: String?, limit: Int?): MatchListResponseDto = matchesResult(groupId)
    override suspend fun getMatchDetail(groupId: String, matchId: String): MatchDetailDto = error("unused")
    override suspend fun editMatch(groupId: String, matchId: String, request: RecordMatchRequestDto): MatchDetailDto = error("unused")
    override suspend fun updateDisplayName(request: com.org.playboard.data.remote.dto.UpdateUserRequestDto): com.org.playboard.data.remote.dto.UserSummaryDto = error("unused")
    override suspend fun uploadUserPhoto(file: okhttp3.MultipartBody.Part): com.org.playboard.data.remote.dto.UserSummaryDto = error("unused")
    override suspend fun deleteMatch(groupId: String, matchId: String) = error("unused")
}

private fun groupDto(id: String = "g1", name: String = "Smashers", matchCount: Int = 12) = GroupDto(
    id = id,
    name = name,
    avatarColor = "#C7EA2B",
    sportCode = "badminton_doubles",
    memberCount = 4,
    matchCount = matchCount,
    myRole = "member",
)

private fun entry(
    rank: Int,
    id: String,
    gp: Int,
    wins: Int,
    pf: Int,
    wr: Double,
    currentStreak: Int = 0,
    bestStreak: Int = 0,
) = LeaderboardEntryDto(rank, id, id, null, "#9ADE28", gp, wins, gp - wins, pf, wr, currentStreak, bestStreak)

private fun playerDto(id: String) = MatchPlayerDto(id, id, "#FF3D8A", null)

private fun matchDto(id: String, t1: List<String>, t2: List<String>, winner: Int, sets: List<Pair<Int, Int>>) =
    MatchSummaryDto(
        id = id,
        playedAt = "2026-07-09T06:58:00Z",
        teams = listOf(
            MatchTeamDto(1, winner == 1, t1.map(::playerDto)),
            MatchTeamDto(2, winner == 2, t2.map(::playerDto)),
        ),
        sets = sets.mapIndexed { i, (a, b) -> MatchSetDto(i + 1, a, b) },
    )

@OptIn(ExperimentalCoroutinesApi::class)
class StatsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(testDispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(api: FakePlayboardApi): Pair<StatsViewModel, com.org.playboard.data.group.GroupRepository> {
        val json = Json { ignoreUnknownKeys = true }
        val groups = testGroupRepository(api, json)
        val vm = StatsViewModel(groups, LeaderboardRepository(api), MatchRepository(api, groups, json))
        return vm to groups
    }

    @Test
    fun `loads records and match-derived sections for the active group`() = runTest(testDispatcher) {
        val api = FakePlayboardApi(
            groupsResult = { GroupsResponseDto(listOf(groupDto(matchCount = 12))) },
            leaderboardResult = {
                LeaderboardResponseDto(
                    listOf(
                        entry(1, "priya", 6, 6, 252, 1.0, currentStreak = 6, bestStreak = 6),
                        entry(2, "raj", 8, 4, 315, 0.5, currentStreak = -1, bestStreak = 3),
                    ),
                )
            },
            matchesResult = {
                MatchListResponseDto(
                    listOf(
                        matchDto("m2", listOf("priya", "dev"), listOf("raj", "kiran"), winner = 1, sets = listOf(21 to 4)),
                        matchDto("m1", listOf("priya", "dev"), listOf("raj", "kiran"), winner = 1, sets = listOf(21 to 15)),
                    ),
                    null,
                )
            },
        )
        val (vm, groups) = viewModel(api)
        groups.refreshGroups()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.isLoading)
        assertTrue(state.hasMatches)
        assertEquals(12, state.records?.totalMatches)
        assertEquals("priya", state.records?.winLeader?.userId)
        assertEquals("raj", state.records?.mostPoints?.userId)
        assertEquals("priya", state.records?.longestStreak?.userId)  // best_streak 6
        assertEquals("priya", state.records?.currentStreak?.userId)  // only positive current run
        assertEquals(6, state.records?.longestStreak?.bestStreak)
        assertNotNull(state.bestPartnership)                          // priya+dev, 2 games
        assertEquals(2, state.bestPartnership?.gamesTogether)
        assertTrue(state.recentForm.isNotEmpty())
        assertEquals("m2", state.biggestWin?.match?.id)               // 21-4 is the bigger margin
    }

    @Test
    fun `a group with no matches shows the empty state`() = runTest(testDispatcher) {
        val api = FakePlayboardApi(
            groupsResult = { GroupsResponseDto(listOf(groupDto(matchCount = 0))) },
            leaderboardResult = { LeaderboardResponseDto(emptyList()) },
            matchesResult = { MatchListResponseDto(emptyList(), null) },
        )
        val (vm, groups) = viewModel(api)
        groups.refreshGroups()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.isLoading)
        assertFalse(state.hasMatches)
        assertFalse(state.noGroup)
    }

    @Test
    fun `a recorded match bumps the revision and reloads silently`() = runTest(testDispatcher) {
        var leaderboardCalls = 0
        val api = FakePlayboardApi(
            groupsResult = { GroupsResponseDto(listOf(groupDto())) },
            leaderboardResult = { leaderboardCalls++; LeaderboardResponseDto(listOf(entry(1, "priya", 6, 6, 252, 1.0))) },
            matchesResult = { MatchListResponseDto(emptyList(), null) },
        )
        val (vm, groups) = viewModel(api)
        groups.refreshGroups()
        advanceUntilIdle()
        val callsAfterInitialLoad = leaderboardCalls

        groups.notifyMatchesChanged()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.isLoading)
        assertTrue(leaderboardCalls > callsAfterInitialLoad)
    }
}
