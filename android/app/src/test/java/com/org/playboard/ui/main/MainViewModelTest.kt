package com.org.playboard.ui.main

import com.org.playboard.data.remote.PlayboardApi
import com.org.playboard.data.remote.dto.AddMemberRequestDto
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
import com.org.playboard.data.remote.dto.RegisterDeviceRequestDto
import com.org.playboard.data.remote.dto.RenameGroupRequestDto
import com.org.playboard.data.remote.dto.TokenResponseDto
import com.org.playboard.data.remote.dto.UnregisterDeviceRequestDto
import com.org.playboard.data.remote.dto.UpdateUserRequestDto
import com.org.playboard.data.remote.dto.UserSummaryDto
import com.org.playboard.testing.testGroupRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.MultipartBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

private class FakePlayboardApi(
    var groupsResult: suspend () -> GroupsResponseDto = { GroupsResponseDto(emptyList()) },
) : PlayboardApi {
    override suspend fun getAppUpdate(): com.org.playboard.data.remote.dto.AppUpdateDto = error("not used in this test")
    override suspend fun downloadApk(url: String): okhttp3.ResponseBody = error("not used in this test")
    override suspend fun signInWithGoogle(request: GoogleSignInRequestDto): TokenResponseDto = error("unused")
    override suspend fun refresh(request: RefreshRequestDto): TokenResponseDto = error("unused")
    override suspend fun getGroups(): GroupsResponseDto = groupsResult()
    override suspend fun createGroup(request: CreateGroupRequestDto): GroupDto = error("unused")
    override suspend fun joinGroup(request: JoinGroupRequestDto): GroupDto = error("unused")
    override suspend fun renameGroup(groupId: String, request: RenameGroupRequestDto): GroupDto = error("unused")
    override suspend fun createInvite(groupId: String, request: CreateInviteRequestDto): InviteResponseDto = error("unused")
    override suspend fun getLeaderboard(groupId: String, from: String?, to: String?): LeaderboardResponseDto = error("unused")
    override suspend fun registerDevice(request: RegisterDeviceRequestDto) = error("unused")
    override suspend fun unregisterDevice(request: UnregisterDeviceRequestDto) = error("unused")
    override suspend fun getMembers(groupId: String): MembersResponseDto = MembersResponseDto(emptyList())
    override suspend fun addMember(groupId: String, request: AddMemberRequestDto): MemberDto = error("unused")
    override suspend fun getPlayerStats(groupId: String, userId: String): PlayerStatsDto = error("unused")
    override suspend fun recordMatch(groupId: String, request: RecordMatchRequestDto): RecordMatchResponseDto = error("unused")
    override suspend fun getMatches(groupId: String, cursor: String?, limit: Int?): MatchListResponseDto = error("unused")
    override suspend fun getMatchDetail(groupId: String, matchId: String): MatchDetailDto = error("unused")
    override suspend fun editMatch(groupId: String, matchId: String, request: RecordMatchRequestDto): MatchDetailDto = error("unused")
    override suspend fun updateDisplayName(request: UpdateUserRequestDto): UserSummaryDto = error("unused")
    override suspend fun uploadUserPhoto(file: MultipartBody.Part): UserSummaryDto = error("unused")
    override suspend fun deleteMatch(groupId: String, matchId: String) = error("unused")
}

private fun groupDto(id: String, name: String) = GroupDto(
    id = id,
    name = name,
    avatarColor = "#C7EA2B",
    sportCode = "badminton_doubles",
    memberCount = 4,
    matchCount = 2,
    myRole = "member",
)

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(testDispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `selectedGroupId defaults to the first group and follows a switch`() = runTest(testDispatcher) {
        val api = FakePlayboardApi(
            groupsResult = { GroupsResponseDto(listOf(groupDto("g1", "Alpha"), groupDto("g2", "Beta"))) },
        )
        val repo = testGroupRepository(api)
        val viewModel = MainViewModel(repo)

        assertNull(viewModel.selectedGroupId.value)

        repo.refreshGroups()
        advanceUntilIdle()
        assertEquals("g1", viewModel.selectedGroupId.value)

        repo.selectGroup("g2")
        advanceUntilIdle()
        assertEquals("g2", viewModel.selectedGroupId.value)
    }
}
