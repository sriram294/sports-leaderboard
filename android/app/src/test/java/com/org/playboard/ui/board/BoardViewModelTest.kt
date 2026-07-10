package com.org.playboard.ui.board

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.org.playboard.data.auth.AuthRepository
import com.org.playboard.data.auth.TokenStore
import com.org.playboard.data.group.GroupRepository
import com.org.playboard.data.leaderboard.LeaderboardRepository
import com.org.playboard.data.remote.PlayboardApi
import com.org.playboard.data.remote.dto.GoogleSignInRequestDto
import com.org.playboard.data.remote.dto.GroupDto
import com.org.playboard.data.remote.dto.GroupsResponseDto
import com.org.playboard.data.remote.dto.LeaderboardEntryDto
import com.org.playboard.data.remote.dto.LeaderboardResponseDto
import com.org.playboard.data.remote.dto.RefreshRequestDto
import com.org.playboard.data.remote.dto.TokenResponseDto
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

private class FakePlayboardApi(
    var groupsResult: suspend () -> GroupsResponseDto = { GroupsResponseDto(emptyList()) },
    var leaderboardResult: suspend (String) -> LeaderboardResponseDto = { LeaderboardResponseDto(emptyList()) },
) : PlayboardApi {
    override suspend fun signInWithGoogle(request: GoogleSignInRequestDto): TokenResponseDto =
        error("not used in this test")

    override suspend fun refresh(request: RefreshRequestDto): TokenResponseDto = error("not used in this test")
    override suspend fun getGroups(): GroupsResponseDto = groupsResult()
    override suspend fun getLeaderboard(groupId: String): LeaderboardResponseDto = leaderboardResult(groupId)
}

private fun groupDto(id: String, name: String) = GroupDto(
    id = id,
    name = name,
    avatarColor = "#C7EA2B",
    sportCode = "badminton_doubles",
    memberCount = 6,
    matchCount = 10,
    myRole = "member",
)

private fun entryDto(rank: Int, name: String, gamesPlayed: Int, wins: Int, pointsFor: Int, winRate: Double) =
    LeaderboardEntryDto(
        rank = rank,
        userId = "user-$name",
        displayName = name,
        photoUrl = null,
        avatarColor = "#FF3D8A",
        gamesPlayed = gamesPlayed,
        wins = wins,
        losses = gamesPlayed - wins,
        pointsFor = pointsFor,
        winRate = winRate,
    )

@OptIn(ExperimentalCoroutinesApi::class)
class BoardViewModelTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var authRepository: AuthRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(testDispatcher),
            produceFile = { tempFolder.newFile("test.preferences_pb") },
        )
        authRepository = AuthRepository(FakePlayboardApi(), TokenStore(dataStore))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(api: FakePlayboardApi) =
        BoardViewModel(authRepository, GroupRepository(api), LeaderboardRepository(api))

    @Test
    fun `loads groups then the first group's leaderboard`() = runTest(testDispatcher) {
        val api = FakePlayboardApi(
            groupsResult = { GroupsResponseDto(listOf(groupDto("g1", "Saturday Smashers"), groupDto("g2", "Office League"))) },
            leaderboardResult = { groupId ->
                assertEquals("g1", groupId)
                LeaderboardResponseDto(listOf(entryDto(1, "Priya", 6, 6, 252, 1.0)))
            },
        )

        val viewModel = viewModel(api)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertFalse(state.hasLoadFailed)
        assertEquals(2, state.groups.size)
        assertEquals("g1", state.selectedGroup?.id)
        assertEquals(listOf("Priya"), state.rankings.map { it.displayName })
    }

    @Test
    fun `switching groups reloads the leaderboard and collapses the switcher`() = runTest(testDispatcher) {
        val api = FakePlayboardApi(
            groupsResult = { GroupsResponseDto(listOf(groupDto("g1", "Saturday Smashers"), groupDto("g2", "Office League"))) },
            leaderboardResult = { groupId ->
                val name = if (groupId == "g1") "Priya" else "Dev"
                LeaderboardResponseDto(listOf(entryDto(1, name, 6, 5, 245, 0.83)))
            },
        )
        val viewModel = viewModel(api)
        advanceUntilIdle()

        viewModel.onGroupSwitcherToggled()
        assertTrue(viewModel.uiState.value.isGroupSwitcherExpanded)

        viewModel.onGroupSelected("g2")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isGroupSwitcherExpanded)
        assertEquals("g2", state.selectedGroup?.id)
        assertEquals(listOf("Dev"), state.rankings.map { it.displayName })
    }

    @Test
    fun `groups fetch failure sets the error flag and retry recovers`() = runTest(testDispatcher) {
        var failing = true
        val api = FakePlayboardApi(
            groupsResult = {
                if (failing) throw RuntimeException("network down")
                GroupsResponseDto(listOf(groupDto("g1", "Saturday Smashers")))
            },
            leaderboardResult = { LeaderboardResponseDto(listOf(entryDto(1, "Priya", 6, 6, 252, 1.0))) },
        )
        val viewModel = viewModel(api)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.hasLoadFailed)
        assertFalse(viewModel.uiState.value.isLoading)

        failing = false
        viewModel.refresh()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.hasLoadFailed)
        assertEquals("g1", state.selectedGroup?.id)
        assertEquals(1, state.rankings.size)
    }

    @Test
    fun `no groups yields the empty state without an error`() = runTest(testDispatcher) {
        val viewModel = viewModel(FakePlayboardApi())
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
                        entryDto(1, "Priya", 6, 6, 252, 1.0),
                        entryDto(2, "Dev", 6, 5, 245, 0.83),
                        entryDto(3, "Raj", 8, 4, 315, 0.5),
                    ),
                )
            },
        )
        val viewModel = viewModel(api)
        advanceUntilIdle()

        viewModel.onSortColumnSelected(RankingSortColumn.POINTS_FOR)

        val state = viewModel.uiState.value
        assertEquals(listOf("Raj", "Priya", "Dev"), state.tableRows.map { it.displayName })
        assertEquals(listOf("Priya", "Dev", "Raj"), state.podium.map { it.displayName })
    }
}
