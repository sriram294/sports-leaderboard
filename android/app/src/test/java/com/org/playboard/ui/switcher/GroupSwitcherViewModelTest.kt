package com.org.playboard.ui.switcher

import com.org.playboard.data.group.GroupRepository
import com.org.playboard.testing.testGroupRepository
import com.org.playboard.data.remote.PlayboardApi
import com.org.playboard.data.remote.dto.AddMemberRequestDto
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
import com.org.playboard.data.remote.dto.MemberDto
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
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

private class FakePlayboardApi(
    var groupsResult: suspend () -> GroupsResponseDto = { GroupsResponseDto(emptyList()) },
    var createGroupResult: suspend (CreateGroupRequestDto) -> GroupDto = { error("createGroup not stubbed") },
    var joinGroupResult: suspend (JoinGroupRequestDto) -> GroupDto = { error("joinGroup not stubbed") },
    var createInviteResult: suspend (String) -> InviteResponseDto = { error("createInvite not stubbed") },
    var addMemberResult: suspend (AddMemberRequestDto) -> MemberDto = { error("addMember not stubbed") },
) : PlayboardApi {
    override suspend fun getAppUpdate(): com.org.playboard.data.remote.dto.AppUpdateDto = error("not used in this test")
    override suspend fun downloadApk(url: String): okhttp3.ResponseBody = error("not used in this test")
    override suspend fun signInWithGoogle(request: GoogleSignInRequestDto): TokenResponseDto =
        error("not used in this test")

    override suspend fun refresh(request: RefreshRequestDto): TokenResponseDto = error("not used in this test")
    override suspend fun getGroups(): GroupsResponseDto = groupsResult()
    override suspend fun createGroup(request: CreateGroupRequestDto): GroupDto = createGroupResult(request)
    override suspend fun joinGroup(request: JoinGroupRequestDto): GroupDto = joinGroupResult(request)
    override suspend fun renameGroup(groupId: String, request: RenameGroupRequestDto): GroupDto = error("not used in this test")
    override suspend fun createInvite(groupId: String, request: CreateInviteRequestDto): InviteResponseDto =
        createInviteResult(groupId)
    override suspend fun getLeaderboard(groupId: String, from: String?, to: String?): LeaderboardResponseDto = error("not used in this test")
    override suspend fun registerDevice(request: com.org.playboard.data.remote.dto.RegisterDeviceRequestDto) = error("not used in this test")
    override suspend fun unregisterDevice(request: com.org.playboard.data.remote.dto.UnregisterDeviceRequestDto) = error("not used in this test")
    override suspend fun getMembers(groupId: String): MembersResponseDto = MembersResponseDto(emptyList())
    override suspend fun addMember(groupId: String, request: AddMemberRequestDto): MemberDto = addMemberResult(request)
    override suspend fun getPlayerStats(groupId: String, userId: String): PlayerStatsDto = error("not used in this test")
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
}

/** A `404` with the backend's `GROUP_INVITE_INVALID` code, as `joinGroup` returns for a bad code. */
private fun invalidInviteHttpException(): HttpException {
    val body = """{"code":"GROUP_INVITE_INVALID"}""".toResponseBody("application/json".toMediaType())
    return HttpException(Response.error<Any>(404, body))
}

/** A `409` with `GROUP_MEMBER_EXISTS`, as `addMember` returns when the person is already in the group. */
private fun memberExistsHttpException(): HttpException {
    val body = """{"code":"GROUP_MEMBER_EXISTS"}""".toResponseBody("application/json".toMediaType())
    return HttpException(Response.error<Any>(409, body))
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

@OptIn(ExperimentalCoroutinesApi::class)
class GroupSwitcherViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(api: FakePlayboardApi) =
        GroupSwitcherViewModel(testGroupRepository(api))

    @Test
    fun `loads groups and selects the first`() = runTest(testDispatcher) {
        val api = FakePlayboardApi(
            groupsResult = { GroupsResponseDto(listOf(groupDto("g1", "Saturday Smashers"), groupDto("g2", "Office League"))) },
        )
        val viewModel = viewModel(api)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(2, state.groups.size)
        assertEquals("g1", state.selectedGroup?.id)
    }

    @Test
    fun `toggling expands then selecting a group collapses`() = runTest(testDispatcher) {
        val api = FakePlayboardApi(
            groupsResult = { GroupsResponseDto(listOf(groupDto("g1", "Saturday Smashers"), groupDto("g2", "Office League"))) },
        )
        val viewModel = viewModel(api)
        advanceUntilIdle()

        viewModel.onToggled()
        assertTrue(viewModel.uiState.value.isExpanded)

        viewModel.onGroupSelected("g2")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isExpanded)
        assertEquals("g2", state.selectedGroup?.id)
    }

    @Test
    fun `creating a group makes it active and closes the sheet`() = runTest(testDispatcher) {
        val api = FakePlayboardApi(
            createGroupResult = { request ->
                assertEquals("badminton_doubles", request.sportCode)
                assertEquals("New Crew", request.name)
                groupDto("g-new", "New Crew")
            },
        )
        val viewModel = viewModel(api)
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.selectedGroup) // starts with no groups

        viewModel.onCreateOrJoinGroupClicked()
        viewModel.onSheetInputChanged("New Crew")
        viewModel.onSheetSubmit()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNull(state.groupActionSheet) // sheet closed on success
        assertEquals("g-new", state.selectedGroup?.id) // new group is active
        assertTrue(state.groups.any { it.id == "g-new" }) // shows in the switcher list
    }

    @Test
    fun `joining with a valid code makes that group active`() = runTest(testDispatcher) {
        val api = FakePlayboardApi(
            joinGroupResult = { request ->
                assertEquals("SMASH42", request.code) // lowercase input is normalized before sending
                groupDto("g-joined", "Joined Crew")
            },
        )
        val viewModel = viewModel(api)
        advanceUntilIdle()

        viewModel.onCreateOrJoinGroupClicked()
        viewModel.onSheetModeChanged(GroupActionMode.JOIN)
        viewModel.onSheetInputChanged("smash42")
        viewModel.onSheetSubmit()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNull(state.groupActionSheet)
        assertEquals("g-joined", state.selectedGroup?.id)
    }

    @Test
    fun `joining with an invalid code shows an inline error and keeps the sheet open`() = runTest(testDispatcher) {
        val api = FakePlayboardApi(joinGroupResult = { throw invalidInviteHttpException() })
        val viewModel = viewModel(api)
        advanceUntilIdle()

        viewModel.onCreateOrJoinGroupClicked()
        viewModel.onSheetModeChanged(GroupActionMode.JOIN)
        viewModel.onSheetInputChanged("NOPE99")
        viewModel.onSheetSubmit()
        advanceUntilIdle()

        val sheet = viewModel.uiState.value.groupActionSheet
        assertNotNull(sheet)
        assertEquals(GroupActionError.INVALID_CODE, sheet?.error)
        assertFalse(sheet!!.isSubmitting)
    }

    @Test
    fun `submitting blank input is ignored`() = runTest(testDispatcher) {
        val api = FakePlayboardApi(createGroupResult = { error("must not be called for blank input") })
        val viewModel = viewModel(api)
        advanceUntilIdle()

        viewModel.onCreateOrJoinGroupClicked()
        viewModel.onSheetSubmit() // no input entered
        advanceUntilIdle()

        val sheet = viewModel.uiState.value.groupActionSheet
        assertNotNull(sheet) // sheet stays open
        assertFalse(sheet!!.isSubmitting)
        assertNull(sheet.error)
    }

    @Test
    fun `returning to the foreground re-syncs so a newly joined member appears`() = runTest(testDispatcher) {
        var memberCount = 6
        val api = FakePlayboardApi(
            groupsResult = {
                GroupsResponseDto(listOf(groupDto("g1", "Saturday Smashers").copy(memberCount = memberCount)))
            },
        )
        val viewModel = viewModel(api)
        advanceUntilIdle()
        assertEquals(6, viewModel.uiState.value.selectedGroup?.memberCount)

        // Someone joined the group from another device while the app was backgrounded.
        memberCount = 7
        viewModel.onAppResumed()
        advanceUntilIdle()

        assertEquals(7, viewModel.uiState.value.selectedGroup?.memberCount)
    }

    @Test
    fun `inviting players generates a code for the active group`() = runTest(testDispatcher) {
        val api = FakePlayboardApi(
            groupsResult = { GroupsResponseDto(listOf(groupDto("g1", "Saturday Smashers", myRole = "owner"))) },
            createInviteResult = { groupId ->
                assertEquals("g1", groupId)
                InviteResponseDto(code = "SMASH42")
            },
        )
        val viewModel = viewModel(api)
        advanceUntilIdle()

        viewModel.onInvitePlayersClicked()
        advanceUntilIdle()

        val sheet = viewModel.uiState.value.inviteSheet
        assertNotNull(sheet)
        assertEquals("Saturday Smashers", sheet!!.groupName)
        assertFalse(sheet.isLoading)
        assertEquals("SMASH42", sheet.code)
        assertFalse(sheet.hasFailed)
    }

    @Test
    fun `adding a member by email closes the sheet on success`() = runTest(testDispatcher) {
        val api = FakePlayboardApi(
            groupsResult = { GroupsResponseDto(listOf(groupDto("g1", "Saturday Smashers", myRole = "owner"))) },
            addMemberResult = { request ->
                assertEquals("sam@gmail.com", request.email)
                assertEquals("Sam", request.displayName)
                MemberDto(userId = "u-sam", displayName = "Sam", photoUrl = null, avatarColor = "#7ED321", role = "member")
            },
        )
        val viewModel = viewModel(api)
        advanceUntilIdle()

        viewModel.onAddMemberClicked()
        viewModel.onAddMemberEmailChanged("sam@gmail.com")
        viewModel.onAddMemberNameChanged("Sam")
        viewModel.onAddMemberSubmit()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.addMemberSheet) // closed on success
    }

    @Test
    fun `adding an already-active member shows the already-member error`() = runTest(testDispatcher) {
        val api = FakePlayboardApi(
            groupsResult = { GroupsResponseDto(listOf(groupDto("g1", "Saturday Smashers", myRole = "owner"))) },
            addMemberResult = { throw memberExistsHttpException() },
        )
        val viewModel = viewModel(api)
        advanceUntilIdle()

        viewModel.onAddMemberClicked()
        viewModel.onAddMemberEmailChanged("dupe@gmail.com")
        viewModel.onAddMemberNameChanged("Dupe")
        viewModel.onAddMemberSubmit()
        advanceUntilIdle()

        val sheet = viewModel.uiState.value.addMemberSheet
        assertNotNull(sheet)
        assertEquals(AddMemberError.ALREADY_MEMBER, sheet?.error)
        assertFalse(sheet!!.isSubmitting)
    }

    @Test
    fun `an invalid email is caught client-side without calling the api`() = runTest(testDispatcher) {
        val api = FakePlayboardApi(
            groupsResult = { GroupsResponseDto(listOf(groupDto("g1", "Saturday Smashers", myRole = "owner"))) },
            addMemberResult = { error("must not call the api for an invalid email") },
        )
        val viewModel = viewModel(api)
        advanceUntilIdle()

        viewModel.onAddMemberClicked()
        viewModel.onAddMemberEmailChanged("notanemail")
        viewModel.onAddMemberNameChanged("Nope")
        viewModel.onAddMemberSubmit()
        advanceUntilIdle()

        assertEquals(AddMemberError.INVALID_EMAIL, viewModel.uiState.value.addMemberSheet?.error)
    }

    @Test
    fun `invite failure is retryable`() = runTest(testDispatcher) {
        var failing = true
        val api = FakePlayboardApi(
            groupsResult = { GroupsResponseDto(listOf(groupDto("g1", "Saturday Smashers", myRole = "owner"))) },
            createInviteResult = {
                if (failing) throw RuntimeException("network down")
                InviteResponseDto(code = "SMASH42")
            },
        )
        val viewModel = viewModel(api)
        advanceUntilIdle()

        viewModel.onInvitePlayersClicked()
        advanceUntilIdle()

        val failed = viewModel.uiState.value.inviteSheet
        assertNotNull(failed)
        assertTrue(failed!!.hasFailed)
        assertNull(failed.code)

        failing = false
        viewModel.onInviteRetry()
        advanceUntilIdle()

        val recovered = viewModel.uiState.value.inviteSheet
        assertFalse(recovered!!.hasFailed)
        assertEquals("SMASH42", recovered.code)
    }
}
