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

private class FakePlayboardApi(
    var groups: List<GroupDto> = emptyList(),
    var stats: Map<String, PlayerStatsDto> = emptyMap(),
) : PlayboardApi {
    var statsCalls = 0

    override suspend fun signInWithGoogle(request: GoogleSignInRequestDto): TokenResponseDto =
        TokenResponseDto("access", "refresh", 900, UserSummaryDto("u1", "Raj", "raj@example.com", null, "#9ADE28"))
    override suspend fun refresh(request: RefreshRequestDto): TokenResponseDto = error("unused")
    override suspend fun getGroups(): GroupsResponseDto = GroupsResponseDto(groups)
    override suspend fun createGroup(request: CreateGroupRequestDto): GroupDto = error("unused")
    override suspend fun joinGroup(request: JoinGroupRequestDto): GroupDto = error("unused")
    override suspend fun renameGroup(groupId: String, request: RenameGroupRequestDto): GroupDto = error("not used in this test")
    override suspend fun createInvite(groupId: String, request: CreateInviteRequestDto): InviteResponseDto = error("unused")
    override suspend fun getLeaderboard(groupId: String): LeaderboardResponseDto = error("unused")
    override suspend fun getMembers(groupId: String): MembersResponseDto = error("unused")
    override suspend fun getPlayerStats(groupId: String, userId: String): PlayerStatsDto {
        statsCalls++
        return stats[userId] ?: error("no stats for $userId")
    }
    override suspend fun recordMatch(groupId: String, request: RecordMatchRequestDto): RecordMatchResponseDto = error("unused")
    override suspend fun getMatches(groupId: String, cursor: String?, limit: Int?): MatchListResponseDto = error("unused")
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

private fun statsDto(userId: String = "u1", withPartner: Boolean = true) = PlayerStatsDto(
    userId = userId,
    displayName = "Raj",
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
    bestPartner = if (withPartner) BestPartnerDto("u2", "Dev", null, "#3DB4FF", 2, 2, 1.0) else null,
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
        val auth = AuthRepository(api, TokenStore(dataStore))
        val groups = testGroupRepository(api, json)
        val stats = StatsRepository(api)
        auth.signInWithGoogle("token")
        groups.refreshGroups()
        return Triple(ProfileViewModel(auth, groups, stats), auth, groups)
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
    fun `bestPartner may be absent`() = runTest(testDispatcher) {
        val api = FakePlayboardApi(groups = listOf(groupDto()), stats = mapOf("u1" to statsDto(withPartner = false)))
        val (viewModel, _, _) = readyViewModel(api)
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.stats?.bestPartner)
    }
}
