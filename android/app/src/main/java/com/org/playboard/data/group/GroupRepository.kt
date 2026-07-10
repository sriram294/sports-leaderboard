package com.org.playboard.data.group

import com.org.playboard.data.model.Group
import com.org.playboard.data.model.Member
import com.org.playboard.data.remote.PlayboardApi
import com.org.playboard.data.remote.apiErrorCode
import com.org.playboard.data.remote.dto.CreateGroupRequestDto
import com.org.playboard.data.remote.dto.CreateInviteRequestDto
import com.org.playboard.data.remote.dto.GroupDto
import com.org.playboard.data.remote.dto.JoinGroupRequestDto
import com.org.playboard.data.remote.dto.MemberDto
import com.org.playboard.data.remote.InvalidInviteCodeException
import com.org.playboard.di.AuthenticatedApi
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.Json

/**
 * Groups the signed-in user belongs to, plus which one is currently active.
 *
 * The active group is app-wide state (the Board/Matches/Add/Profile headers
 * all scope to it — docs/requirements/00-overview.md § Group), so it lives
 * here rather than in any one screen's ViewModel. Selection is in-memory
 * only for now: on a fresh process the first group is active again.
 */
@Singleton
class GroupRepository @Inject constructor(
    @AuthenticatedApi private val api: PlayboardApi,
    private val json: Json,
) {
    private val _groups = MutableStateFlow<List<Group>>(emptyList())
    val groups: StateFlow<List<Group>> = _groups.asStateFlow()

    private val _selectedGroupId = MutableStateFlow<String?>(null)

    /**
     * Bumped whenever match data changes (e.g. a match is recorded), so screens
     * showing derived data — the Board leaderboard — can re-fetch. Kept here
     * because the active group is already the app-wide coordination point.
     */
    private val _dataRevision = MutableStateFlow(0)
    val dataRevision: StateFlow<Int> = _dataRevision.asStateFlow()

    fun notifyMatchesChanged() {
        _dataRevision.update { it + 1 }
    }

    /**
     * The active group — the explicit selection if it still exists, else the
     * first group the user belongs to, else `null` (user has no groups yet).
     */
    val selectedGroup: Flow<Group?> = combine(_groups, _selectedGroupId) { groups, selectedId ->
        groups.firstOrNull { it.id == selectedId } ?: groups.firstOrNull()
    }

    /** Re-fetches the user's groups from the backend, updating [groups] on success. */
    suspend fun refreshGroups(): Result<List<Group>> =
        runCatching { api.getGroups().groups.map(GroupDto::toGroup) }
            .onSuccess { _groups.value = it }

    /** The active group's roster, for building teams in Add Match. */
    suspend fun getMembers(groupId: String): Result<List<Member>> =
        runCatching { api.getMembers(groupId).members.map(MemberDto::toMember) }

    /**
     * Creates a group (the caller becomes its owner) and makes it active so the
     * Board switches to it immediately. Only one sport exists today, so
     * [SPORT_CODE] is fixed rather than chosen.
     */
    suspend fun createGroup(name: String): Result<Group> =
        runCatching { api.createGroup(CreateGroupRequestDto(name.trim(), SPORT_CODE)).toGroup() }
            .onSuccess(::addAndSelect)

    /**
     * Joins a group by invite code and makes it active. A wrong/expired/exhausted
     * code (`GROUP_INVITE_INVALID`) is surfaced as [InvalidInviteCodeException] so
     * the UI can tell it apart from a network failure.
     */
    suspend fun joinGroup(code: String): Result<Group> =
        runCatching { api.joinGroup(JoinGroupRequestDto(code.trim().uppercase())).toGroup() }
            .onSuccess(::addAndSelect)
            .recoverCatching { cause ->
                throw if (cause.apiErrorCode(json) == "GROUP_INVITE_INVALID") InvalidInviteCodeException() else cause
            }

    /**
     * Creates a shareable invite code for [groupId] (unlimited uses, no expiry).
     * Owner/admin only — the backend rejects other members — so callers should
     * gate this on [Group.canInvite].
     */
    suspend fun createInvite(groupId: String): Result<String> =
        runCatching { api.createInvite(groupId, CreateInviteRequestDto()).code }

    fun selectGroup(groupId: String) {
        _selectedGroupId.value = groupId
    }

    /** Inserts [group] (or replaces the existing entry with the same id) and selects it. */
    private fun addAndSelect(group: Group) {
        _groups.update { current ->
            if (current.any { it.id == group.id }) {
                current.map { if (it.id == group.id) group else it }
            } else {
                current + group
            }
        }
        selectGroup(group.id)
    }

    private companion object {
        // The only sport seeded server-side (V1 migration); no sport picker yet.
        const val SPORT_CODE = "badminton_doubles"
    }
}

private fun MemberDto.toMember() = Member(
    id = userId,
    displayName = displayName,
    photoUrl = photoUrl,
    avatarColor = avatarColor,
    role = role,
)

private fun GroupDto.toGroup() = Group(
    id = id,
    name = name,
    avatarColor = avatarColor,
    memberCount = memberCount,
    matchCount = matchCount,
    myRole = myRole,
)
