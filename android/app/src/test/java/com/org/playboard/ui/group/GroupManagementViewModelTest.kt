package com.org.playboard.ui.group

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.org.playboard.data.auth.AuthRepository
import com.org.playboard.data.auth.TokenStore
import com.org.playboard.data.remote.PlayboardApi
import com.org.playboard.data.remote.dto.GroupDto
import com.org.playboard.data.remote.dto.GroupsResponseDto
import com.org.playboard.data.remote.dto.MemberDto
import com.org.playboard.data.remote.dto.MembersResponseDto
import com.org.playboard.data.remote.dto.TokenResponseDto
import com.org.playboard.data.remote.dto.UpdateRoleRequestDto
import com.org.playboard.data.remote.dto.UpdateSessionRequestDto
import com.org.playboard.data.remote.dto.UserSummaryDto
import com.org.playboard.testing.testGroupRepository
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class GroupManagementViewModelTest {

    @get:Rule val tempFolder = TemporaryFolder()
    private val testDispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(testDispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private suspend fun readyViewModel(api: FakeApi): GroupManagementViewModel {
        val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(testDispatcher),
            produceFile = { tempFolder.newFile("ds-${System.nanoTime()}.preferences_pb") },
        )
        val json = Json { ignoreUnknownKeys = true }
        val auth = AuthRepository(api, TokenStore(dataStore), com.org.playboard.data.device.DeviceRegistrar(api))
        val groups = testGroupRepository(api, json)
        auth.signInWithGoogle("token") // signed-in user id = "u1" (the owner)
        groups.refreshGroups()
        return GroupManagementViewModel(auth, groups)
    }

    @Test
    fun `lists only groups the user manages`() = runTest(testDispatcher) {
        val api = FakeApi(
            groups = mutableListOf(
                groupDto("g1", "Owned", "owner"),
                groupDto("g2", "Admin Of", "admin"),
                groupDto("g3", "Just A Member", "member"),
            ),
        )
        val vm = readyViewModel(api)
        advanceUntilIdle()
        assertEquals(listOf("g1", "g2"), vm.uiState.value.managedGroups.map { it.id })
    }

    @Test
    fun `selecting a group loads its non-guest members`() = runTest(testDispatcher) {
        val api = FakeApi(groups = mutableListOf(groupDto("g1", "Owned", "owner")))
        api.members["g1"] = mutableListOf(member("u1", "owner"), member("u2", "member"))
        api.guests["g1"] = mutableListOf(member("guest1", "guest"))
        val vm = readyViewModel(api)
        advanceUntilIdle()

        vm.onSelectGroup("g1")
        advanceUntilIdle()
        assertEquals("g1", vm.uiState.value.selectedGroupId)
        assertEquals(listOf("u1", "u2"), vm.uiState.value.members.map { it.id }) // guest excluded
    }

    @Test
    fun `removing a member drops them from the roster`() = runTest(testDispatcher) {
        val api = FakeApi(groups = mutableListOf(groupDto("g1", "Owned", "owner")))
        api.members["g1"] = mutableListOf(member("u1", "owner"), member("u2", "member"))
        val vm = readyViewModel(api)
        advanceUntilIdle()
        vm.onSelectGroup("g1"); advanceUntilIdle()

        vm.onRemoveMember("u2")
        advanceUntilIdle()
        assertFalse(vm.uiState.value.busy)
        assertEquals(listOf("u1"), vm.uiState.value.members.map { it.id })
    }

    @Test
    fun `changing a role updates the roster`() = runTest(testDispatcher) {
        val api = FakeApi(groups = mutableListOf(groupDto("g1", "Owned", "owner")))
        api.members["g1"] = mutableListOf(member("u1", "owner"), member("u2", "member"))
        val vm = readyViewModel(api)
        advanceUntilIdle()
        vm.onSelectGroup("g1"); advanceUntilIdle()

        vm.onChangeRole("u2", "admin")
        advanceUntilIdle()
        assertEquals("admin", vm.uiState.value.members.first { it.id == "u2" }.role)
    }

    @Test
    fun `saving a session window updates the group`() = runTest(testDispatcher) {
        val api = FakeApi(groups = mutableListOf(groupDto("g1", "Owned", "owner")))
        val vm = readyViewModel(api)
        advanceUntilIdle()
        vm.onSelectGroup("g1"); advanceUntilIdle()

        vm.onSetSession("19:00", "21:00")
        advanceUntilIdle()
        val group = vm.uiState.value.selectedGroup
        assertNotNull(group)
        assertEquals("19:00", group?.sessionStart)
        assertEquals("21:00", group?.sessionEnd)
    }

    @Test
    fun `a failed action surfaces an error and clears busy`() = runTest(testDispatcher) {
        val api = object : FakeApi(groups = mutableListOf(groupDto("g1", "Owned", "owner"))) {
            override suspend fun removeMember(groupId: String, userId: String) = error("network down")
        }
        api.members["g1"] = mutableListOf(member("u1", "owner"), member("u2", "member"))
        val vm = readyViewModel(api)
        advanceUntilIdle()
        vm.onSelectGroup("g1"); advanceUntilIdle()

        vm.onRemoveMember("u2")
        advanceUntilIdle()
        val state = vm.uiState.value
        assertFalse(state.busy)
        assertNotNull(state.actionError)
        assertEquals(listOf("u1", "u2"), state.members.map { it.id }) // unchanged
    }

    @Test
    fun `canRemove and canChangeRoles enforce the role rules`() {
        val owned = modelGroup("g1", "owner")
        val adminOf = modelGroup("g2", "admin")
        val state = GroupManagementUiState(currentUserId = "u1")

        // Owner viewer: can remove members and admins (not self/owner/guest); can change roles.
        assertTrue(state.canChangeRoles(owned))
        assertTrue(state.canRemove(owned, modelMember("u2", "member")))
        assertTrue(state.canRemove(owned, modelMember("u3", "admin")))
        assertFalse(state.canRemove(owned, modelMember("u1", "owner"))) // self + owner
        assertFalse(state.canRemove(owned, modelMember("guest1", "guest")))

        // Admin viewer: can't change roles; can remove members but not other admins.
        assertFalse(state.canChangeRoles(adminOf))
        assertTrue(state.canRemove(adminOf, modelMember("u2", "member")))
        assertFalse(state.canRemove(adminOf, modelMember("u3", "admin")))
    }

    // ── helpers ── (domain-model variants for the pure canRemove/canChangeRoles checks)
    private fun modelMember(id: String, role: String) =
        com.org.playboard.data.model.Member(id, "User $id", null, null, "#7ED321", role)

    private fun modelGroup(id: String, role: String) =
        com.org.playboard.data.model.Group(id, "G$id", "#7ED321", 2, 0, role)
}

private fun groupDto(id: String, name: String, role: String) =
    GroupDto(id, name, "#7ED321", "badminton_doubles", 2, 0, role, null, null)

private fun member(id: String, role: String) = MemberDto(id, "User $id", null, null, "#7ED321", role)

private open class FakeApi(
    val groups: MutableList<GroupDto> = mutableListOf(),
    val members: MutableMap<String, MutableList<MemberDto>> = mutableMapOf(),
    val guests: MutableMap<String, MutableList<MemberDto>> = mutableMapOf(),
) : PlayboardApi {
    override suspend fun signInWithGoogle(request: com.org.playboard.data.remote.dto.GoogleSignInRequestDto): TokenResponseDto =
        TokenResponseDto("access", "refresh", 900, UserSummaryDto("u1", "Owner", "owner@example.com", null, null, "#7ED321"))
    override suspend fun getGroups(): GroupsResponseDto = GroupsResponseDto(groups.toList())
    override suspend fun getMembers(groupId: String): MembersResponseDto =
        MembersResponseDto(members[groupId].orEmpty().toList(), guests[groupId].orEmpty().toList())
    override suspend fun removeMember(groupId: String, userId: String) {
        members[groupId]?.removeAll { it.userId == userId }
    }
    override suspend fun changeMemberRole(groupId: String, userId: String, request: UpdateRoleRequestDto): MemberDto {
        val list = members.getValue(groupId)
        val idx = list.indexOfFirst { it.userId == userId }
        val updated = list[idx].copy(role = request.role)
        list[idx] = updated
        return updated
    }
    override suspend fun updateSession(groupId: String, request: UpdateSessionRequestDto): GroupDto {
        val idx = groups.indexOfFirst { it.id == groupId }
        val updated = groups[idx].copy(sessionStart = request.start, sessionEnd = request.end)
        groups[idx] = updated
        return updated
    }
    override suspend fun createInvite(groupId: String, request: com.org.playboard.data.remote.dto.CreateInviteRequestDto): com.org.playboard.data.remote.dto.InviteResponseDto =
        com.org.playboard.data.remote.dto.InviteResponseDto("ABC123", null)

    override suspend fun getAppUpdate(): com.org.playboard.data.remote.dto.AppUpdateDto = error("unused")
    override suspend fun downloadApk(url: String): okhttp3.ResponseBody = error("unused")
    override suspend fun refresh(request: com.org.playboard.data.remote.dto.RefreshRequestDto): TokenResponseDto = error("unused")
    override suspend fun createGroup(request: com.org.playboard.data.remote.dto.CreateGroupRequestDto): GroupDto = error("unused")
    override suspend fun joinGroup(request: com.org.playboard.data.remote.dto.JoinGroupRequestDto): GroupDto = error("unused")
    override suspend fun renameGroup(groupId: String, request: com.org.playboard.data.remote.dto.RenameGroupRequestDto): GroupDto = error("unused")
    override suspend fun getLeaderboard(groupId: String, from: String?, to: String?): com.org.playboard.data.remote.dto.LeaderboardResponseDto = error("unused")
    override suspend fun registerDevice(request: com.org.playboard.data.remote.dto.RegisterDeviceRequestDto) = error("unused")
    override suspend fun unregisterDevice(request: com.org.playboard.data.remote.dto.UnregisterDeviceRequestDto) = error("unused")
    override suspend fun addMember(groupId: String, request: com.org.playboard.data.remote.dto.AddMemberRequestDto): MemberDto = error("unused")
    override suspend fun getPlayerStats(groupId: String, userId: String): com.org.playboard.data.remote.dto.PlayerStatsDto = error("unused")
    override suspend fun getPlayerAttendance(groupId: String, userId: String, from: String, to: String): com.org.playboard.data.remote.dto.PlayerAttendanceDto = com.org.playboard.data.remote.dto.PlayerAttendanceDto()
    override suspend fun recordMatch(groupId: String, request: com.org.playboard.data.remote.dto.RecordMatchRequestDto): com.org.playboard.data.remote.dto.RecordMatchResponseDto = error("unused")
    override suspend fun getMatches(groupId: String, cursor: String?, limit: Int?, mine: Boolean?): com.org.playboard.data.remote.dto.MatchListResponseDto = error("unused")
    override suspend fun getMatchDetail(groupId: String, matchId: String): com.org.playboard.data.remote.dto.MatchDetailDto = error("unused")
    override suspend fun editMatch(groupId: String, matchId: String, request: com.org.playboard.data.remote.dto.RecordMatchRequestDto): com.org.playboard.data.remote.dto.MatchDetailDto = error("unused")
    override suspend fun deleteMatch(groupId: String, matchId: String) = error("unused")
    override suspend fun updateDisplayName(request: com.org.playboard.data.remote.dto.UpdateUserRequestDto): UserSummaryDto = error("unused")
    override suspend fun uploadUserPhoto(file: okhttp3.MultipartBody.Part): UserSummaryDto = error("unused")
    override suspend fun updateAvatar(request: com.org.playboard.data.remote.dto.UpdateAvatarRequestDto): UserSummaryDto = error("unused")
}
