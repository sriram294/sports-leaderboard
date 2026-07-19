package com.org.playboard.ui.matches

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.org.playboard.data.auth.AuthRepository
import com.org.playboard.data.auth.TokenStore
import com.org.playboard.data.group.GroupRepository
import com.org.playboard.testing.testGroupRepository
import com.org.playboard.data.match.MatchRepository
import com.org.playboard.data.model.MatchDetail
import com.org.playboard.data.model.MatchSet
import com.org.playboard.data.model.MatchTeam
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
import com.org.playboard.data.remote.dto.MatchSummaryDto
import com.org.playboard.data.remote.dto.MatchTeamDto
import com.org.playboard.data.remote.dto.MembersResponseDto
import com.org.playboard.data.remote.dto.PlayerStatsDto
import com.org.playboard.data.remote.dto.RecordMatchRequestDto
import com.org.playboard.data.remote.dto.RecordMatchResponseDto
import com.org.playboard.data.remote.dto.RecordedByDto
import com.org.playboard.data.remote.dto.RefreshRequestDto
import com.org.playboard.data.remote.dto.TokenResponseDto
import java.time.Instant
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
    val matches: MutableList<MatchSummaryDto> = mutableListOf(),
    var details: Map<String, MatchDetailDto> = emptyMap(),
) : PlayboardApi {
    override suspend fun getAppUpdate(): com.org.playboard.data.remote.dto.AppUpdateDto = error("not used in this test")
    override suspend fun downloadApk(url: String): okhttp3.ResponseBody = error("not used in this test")
    val deletedIds = mutableListOf<String>()
    // The "caller" the mine=true filter scopes to (the backend uses the auth principal).
    var mineUserId: String = "u1"

    override suspend fun signInWithGoogle(request: GoogleSignInRequestDto): TokenResponseDto = error("unused")
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
    override suspend fun getPlayerStats(groupId: String, userId: String): PlayerStatsDto = error("unused")
    override suspend fun getPlayerAttendance(groupId: String, userId: String, from: String, to: String): com.org.playboard.data.remote.dto.PlayerAttendanceDto = com.org.playboard.data.remote.dto.PlayerAttendanceDto()
    override suspend fun recordMatch(groupId: String, request: RecordMatchRequestDto): RecordMatchResponseDto = error("unused")
    override suspend fun getMatches(groupId: String, cursor: String?, limit: Int?, mine: Boolean?): MatchListResponseDto {
        // mine=true scopes to matches the caller (mineUserId) played in, like the backend.
        val source = if (mine == true) {
            matches.filter { m -> m.teams.any { t -> t.players.any { it.userId == mineUserId } } }
        } else {
            matches.toList()
        }
        // Cursor is a plain offset into the newest-first list; nextCursor is set while
        // more matches remain, mirroring the backend's keyset pagination.
        val offset = cursor?.toInt() ?: 0
        val pageSize = limit ?: source.size
        val page = source.drop(offset).take(pageSize)
        val nextOffset = offset + page.size
        val nextCursor = if (nextOffset < source.size) nextOffset.toString() else null
        return MatchListResponseDto(page, nextCursor)
    }
    override suspend fun getMatchDetail(groupId: String, matchId: String): MatchDetailDto =
        details[matchId] ?: error("no detail for $matchId")
    override suspend fun editMatch(groupId: String, matchId: String, request: RecordMatchRequestDto): MatchDetailDto =
        error("unused")
    override suspend fun updateDisplayName(request: com.org.playboard.data.remote.dto.UpdateUserRequestDto): com.org.playboard.data.remote.dto.UserSummaryDto = error("unused")
    override suspend fun uploadUserPhoto(file: okhttp3.MultipartBody.Part): com.org.playboard.data.remote.dto.UserSummaryDto = error("unused")
    override suspend fun updateAvatar(request: com.org.playboard.data.remote.dto.UpdateAvatarRequestDto): com.org.playboard.data.remote.dto.UserSummaryDto = error("unused")
    override suspend fun deleteMatch(groupId: String, matchId: String) {
        deletedIds.add(matchId)
        matches.removeAll { it.id == matchId }
    }
}

private fun groupDto(myRole: String = "member") = GroupDto(
    id = "g1",
    name = "Smashers",
    avatarColor = "#C7EA2B",
    sportCode = "badminton_doubles",
    memberCount = 4,
    matchCount = 2,
    myRole = myRole,
)

private fun summary(id: String, playedAt: String, winner: Int = 1) = MatchSummaryDto(
    id = id,
    playedAt = playedAt,
    teams = listOf(
        MatchTeamDto(1, winner == 1, listOf(player("u1", "Raj"), player("u2", "Dev"))),
        MatchTeamDto(2, winner == 2, listOf(player("u3", "Marcus"), player("u4", "Kiran"))),
    ),
    sets = listOf(MatchSetDto(1, 21, 12)),
)

/** A match the signed-in user (u1) did NOT play in — for the "my matches" filter. */
private fun summaryNoU1(id: String, playedAt: String) = MatchSummaryDto(
    id = id,
    playedAt = playedAt,
    teams = listOf(
        MatchTeamDto(1, true, listOf(player("u5", "Sam"), player("u6", "Lee"))),
        MatchTeamDto(2, false, listOf(player("u3", "Marcus"), player("u4", "Kiran"))),
    ),
    sets = listOf(MatchSetDto(1, 21, 12)),
)

private fun detailDto(id: String, recordedByUserId: String) = MatchDetailDto(
    id = id,
    playedAt = "2026-07-09T12:00:00Z",
    teams = summary(id, "2026-07-09T12:00:00Z").teams,
    sets = summary(id, "2026-07-09T12:00:00Z").sets,
    recordedBy = RecordedByDto(recordedByUserId, "Raj"),
    recordedAt = "2026-07-09T12:00:00Z",
    events = emptyList(),
)

private fun player(id: String, name: String) = MatchPlayerDto(id, name, "#FF3D8A", null)

/** [count] matches, newest first, one minute apart on the same day. */
private fun manyMatches(count: Int): MutableList<MatchSummaryDto> {
    val base = Instant.parse("2026-07-09T12:00:00Z")
    return (1..count).mapTo(mutableListOf()) { i ->
        summary("m$i", base.minusSeconds(i * 60L).toString())
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class MatchesViewModelTest {

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

    private suspend fun readyViewModel(api: FakePlayboardApi): MatchesViewModel {
        val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(testDispatcher),
            produceFile = { tempFolder.newFile("ds-${System.nanoTime()}.preferences_pb") },
        )
        val json = Json { ignoreUnknownKeys = true }
        val auth = AuthRepository(api, TokenStore(dataStore), com.org.playboard.data.device.DeviceRegistrar(api))
        val groups = testGroupRepository(api, json)
        val matches = MatchRepository(api, groups, json)
        groups.refreshGroups()
        return MatchesViewModel(auth, groups, matches)
    }

    @Test
    fun `loads matches and groups them by date, newest first`() = runTest(testDispatcher) {
        val api = FakePlayboardApi(
            groups = listOf(groupDto()),
            matches = mutableListOf(
                summary("m1", "2026-07-09T12:00:00Z"),
                summary("m2", "2026-07-09T09:00:00Z"),
                summary("m3", "2026-07-08T12:00:00Z"),
            ),
        )
        val viewModel = readyViewModel(api)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(3, state.matchCount)
        assertEquals(2, state.sections.size)          // two distinct days
        assertEquals(2, state.sections[0].matches.size) // 09 Jul has two
    }

    @Test
    fun `empty group shows no matches`() = runTest(testDispatcher) {
        val api = FakePlayboardApi(groups = listOf(groupDto()))
        val viewModel = readyViewModel(api)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertTrue(state.matches.isEmpty())
    }

    @Test
    fun `expanding a match fetches its detail and collapsing clears it`() = runTest(testDispatcher) {
        val api = FakePlayboardApi(
            groups = listOf(groupDto()),
            matches = mutableListOf(summary("m1", "2026-07-09T12:00:00Z")),
            details = mapOf("m1" to detailDto("m1", recordedByUserId = "u1")),
        )
        val viewModel = readyViewModel(api)
        advanceUntilIdle()

        viewModel.onMatchClicked("m1")
        advanceUntilIdle()
        assertEquals("m1", viewModel.uiState.value.expandedId)
        assertEquals("m1", viewModel.uiState.value.detail?.id)

        viewModel.onMatchClicked("m1")
        assertNull(viewModel.uiState.value.expandedId)
        assertNull(viewModel.uiState.value.detail)
    }

    @Test
    fun `deleting a match removes it after confirmation`() = runTest(testDispatcher) {
        val api = FakePlayboardApi(
            groups = listOf(groupDto()),
            matches = mutableListOf(
                summary("m1", "2026-07-09T12:00:00Z"),
                summary("m2", "2026-07-08T12:00:00Z"),
            ),
        )
        val viewModel = readyViewModel(api)
        advanceUntilIdle()

        viewModel.onDeleteClicked("m1")
        assertEquals("m1", viewModel.uiState.value.deleteTargetId)

        viewModel.onDeleteConfirmed()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNull(state.deleteTargetId)
        assertFalse(state.isDeleting)
        assertEquals(listOf("m2"), state.matches.map { it.id })
        assertEquals(listOf("m1"), api.deletedIds)
    }

    @Test
    fun `loads only the first page and can load more`() = runTest(testDispatcher) {
        val api = FakePlayboardApi(groups = listOf(groupDto()), matches = manyMatches(25))
        val viewModel = readyViewModel(api)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(20, state.matchCount)   // PAGE_SIZE
        assertTrue(state.canLoadMore)
    }

    @Test
    fun `loadMore appends the next page and stops when exhausted`() = runTest(testDispatcher) {
        val api = FakePlayboardApi(groups = listOf(groupDto()), matches = manyMatches(25))
        val viewModel = readyViewModel(api)
        advanceUntilIdle()

        viewModel.loadMore()
        advanceUntilIdle()
        var state = viewModel.uiState.value
        assertEquals(25, state.matchCount)
        assertFalse(state.isLoadingMore)
        assertFalse(state.canLoadMore)

        // Fully loaded → further loadMore is a no-op.
        viewModel.loadMore()
        advanceUntilIdle()
        state = viewModel.uiState.value
        assertEquals(25, state.matchCount)
    }

    @Test
    fun `pull to refresh resets pagination to the first page`() = runTest(testDispatcher) {
        val api = FakePlayboardApi(groups = listOf(groupDto()), matches = manyMatches(25))
        val viewModel = readyViewModel(api)
        advanceUntilIdle()
        viewModel.loadMore()
        advanceUntilIdle()
        assertEquals(25, viewModel.uiState.value.matchCount)

        viewModel.onPullRefresh()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(20, state.matchCount)
        assertTrue(state.canLoadMore)
    }

    @Test
    fun `newest day defaults expanded, older days collapsed, and toggling flips both`() = runTest(testDispatcher) {
        val api = FakePlayboardApi(
            groups = listOf(groupDto()),
            matches = mutableListOf(
                summary("m1", "2026-07-09T12:00:00Z"),
                summary("m2", "2026-07-08T12:00:00Z"),
            ),
        )
        val viewModel = readyViewModel(api)
        advanceUntilIdle()

        // Derive dates from sections so the assertion is timezone-independent.
        val newest = viewModel.uiState.value.sections[0].date
        val older = viewModel.uiState.value.sections[1].date
        assertTrue(viewModel.uiState.value.isDateExpanded(newest))
        assertFalse(viewModel.uiState.value.isDateExpanded(older))

        viewModel.onDateToggled(older)
        assertTrue(viewModel.uiState.value.isDateExpanded(older))

        viewModel.onDateToggled(newest)
        assertFalse(viewModel.uiState.value.isDateExpanded(newest))
    }

    @Test
    fun `my matches filter scopes to the user's matches and toggles back`() = runTest(testDispatcher) {
        val api = FakePlayboardApi(
            groups = listOf(groupDto()),
            matches = mutableListOf(
                summary("m1", "2026-07-09T12:00:00Z"),      // u1 plays
                summary("m2", "2026-07-09T09:00:00Z"),      // u1 plays
                summaryNoU1("m3", "2026-07-08T12:00:00Z"),  // u1 absent
            ),
        )
        val viewModel = readyViewModel(api)
        advanceUntilIdle()
        assertEquals(3, viewModel.uiState.value.matchCount)
        assertFalse(viewModel.uiState.value.showMineOnly)

        viewModel.onToggleMineOnly()
        advanceUntilIdle()
        val filtered = viewModel.uiState.value
        assertTrue(filtered.showMineOnly)
        assertEquals(listOf("m1", "m2"), filtered.matches.map { it.id })

        viewModel.onToggleMineOnly()
        advanceUntilIdle()
        val all = viewModel.uiState.value
        assertFalse(all.showMineOnly)
        assertEquals(3, all.matchCount)
    }

    @Test
    fun `moderator flag comes from the group role`() = runTest(testDispatcher) {
        val owner = readyViewModel(FakePlayboardApi(groups = listOf(groupDto(myRole = "owner"))))
        advanceUntilIdle()
        assertTrue(owner.uiState.value.canModerate)

        val member = readyViewModel(FakePlayboardApi(groups = listOf(groupDto(myRole = "member"))))
        advanceUntilIdle()
        assertFalse(member.uiState.value.canModerate)
    }

    @Test
    fun `canModify allows the recorder or a moderator only`() {
        val detail = MatchDetail(
            id = "m1",
            playedAt = Instant.parse("2026-07-09T12:00:00Z"),
            teams = listOf(MatchTeam(1, true, emptyList()), MatchTeam(2, false, emptyList())),
            sets = listOf(MatchSet(1, 21, 12)),
            recordedByUserId = "u1",
            recordedByName = "Raj",
            recordedAt = Instant.parse("2026-07-09T12:00:00Z"),
            events = emptyList(),
        )
        assertTrue(MatchesUiState(canModerate = true, currentUserId = "u2").canModify(detail))   // moderator
        assertTrue(MatchesUiState(canModerate = false, currentUserId = "u1").canModify(detail))  // recorder
        assertFalse(MatchesUiState(canModerate = false, currentUserId = "u2").canModify(detail)) // neither
    }
}
