package com.org.playboard.data.group

import com.org.playboard.data.model.Group
import com.org.playboard.data.model.Member
import com.org.playboard.data.remote.PlayboardApi
import com.org.playboard.data.remote.apiErrorCode
import com.org.playboard.data.remote.dto.AddMemberRequestDto
import com.org.playboard.data.remote.dto.CreateGroupRequestDto
import com.org.playboard.data.remote.dto.CreateInviteRequestDto
import com.org.playboard.data.remote.dto.GroupDto
import com.org.playboard.data.remote.dto.JoinGroupRequestDto
import com.org.playboard.data.remote.dto.MemberDto
import com.org.playboard.data.remote.dto.RenameGroupRequestDto
import com.org.playboard.data.remote.InvalidInviteCodeException
import com.org.playboard.data.remote.MemberAlreadyExistsException
import com.org.playboard.di.AppScope
import com.org.playboard.di.AuthenticatedApi
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/** Load status of the app-wide group list (see [GroupRepository.groupsLoadState]). */
enum class GroupsLoadState { LOADING, LOADED, FAILED }

/**
 * Groups the signed-in user belongs to, plus which one is currently active.
 *
 * The active group is app-wide state (the Board/Matches/Add/Profile headers
 * all scope to it — docs/requirements/00-overview.md § Group), so it lives
 * here rather than in any one screen's ViewModel. The selection persists
 * across relaunches via [SelectedGroupStore]; an in-session pick takes
 * precedence over the saved id until the process restarts.
 */
@Singleton
class GroupRepository @Inject constructor(
    @AuthenticatedApi private val api: PlayboardApi,
    private val json: Json,
    private val selectedGroupStore: SelectedGroupStore,
    @AppScope private val appScope: CoroutineScope,
) {
    private val _groups = MutableStateFlow<List<Group>>(emptyList())
    val groups: StateFlow<List<Group>> = _groups.asStateFlow()

    /**
     * Load status of the group list, so screens can tell "still loading" apart
     * from "genuinely no groups" apart from "the fetch failed" — all three of
     * which otherwise look like an empty [selectedGroup]. Owned here because the
     * group list is app-wide state shared by the switcher and every tab.
     */
    private val _groupsLoadState = MutableStateFlow(GroupsLoadState.LOADING)
    val groupsLoadState: StateFlow<GroupsLoadState> = _groupsLoadState.asStateFlow()

    private val _selectedGroupId = MutableStateFlow<String?>(null)

    /**
     * Bumped whenever app data may have changed — a local match mutation, or a
     * manual/foreground [refresh] — so screens showing derived data (the Board
     * leaderboard, Matches log, Profile stats) can re-fetch. Kept here because the
     * active group is already the app-wide coordination point.
     */
    private val _dataRevision = MutableStateFlow(0)
    val dataRevision: StateFlow<Int> = _dataRevision.asStateFlow()

    /**
     * A locally recorded/edited/deleted match changed the leaderboard and the
     * group's match count. Bump the revision (fire-and-forget, so it never blocks
     * the record round-trip) and silently re-sync the group list so counts stay
     * current.
     */
    fun notifyMatchesChanged() {
        _dataRevision.update { it + 1 }
        appScope.launch { refreshGroups(showLoading = false) }
    }

    /**
     * A foreground / pull-to-refresh resync: silently re-fetch the group list
     * (member & match counts), then bump [dataRevision] so every observing screen
     * reloads its derived data. This is how changes made by *other* members — most
     * visibly, someone joining the group — show up without a relogin; previously
     * the one-shot startup fetch was only re-run by signing out and back in.
     */
    suspend fun refresh() {
        refreshGroups(showLoading = false)
        _dataRevision.update { it + 1 }
    }

    /**
     * The active group — an in-session pick if made, else the id persisted from
     * a previous run, resolved against the loaded groups. Falls back to the first
     * group when that id no longer exists (e.g. the user left it), else `null`
     * (user has no groups yet).
     */
    val selectedGroup: Flow<Group?> =
        combine(_groups, _selectedGroupId, selectedGroupStore.selectedGroupId) { groups, sessionId, savedId ->
            val activeId = sessionId ?: savedId
            groups.firstOrNull { it.id == activeId } ?: groups.firstOrNull()
        }

    /**
     * Re-fetches the user's groups from the backend, updating [groups] on success.
     * [showLoading] flips [groupsLoadState] to LOADING first — right for the initial
     * load and explicit retries. A silent refresh (foreground resync, pull-to-refresh)
     * passes `false`: it keeps the last-known list on screen and only reports FAILED
     * when there's nothing loaded yet, so a transient network blip doesn't wipe the UI.
     */
    suspend fun refreshGroups(showLoading: Boolean = true): Result<List<Group>> {
        if (showLoading) _groupsLoadState.value = GroupsLoadState.LOADING
        return runCatching { api.getGroups().groups.map(GroupDto::toGroup) }
            .onSuccess {
                _groups.value = it
                _groupsLoadState.value = GroupsLoadState.LOADED
            }
            .onFailure {
                if (showLoading || _groups.value.isEmpty()) _groupsLoadState.value = GroupsLoadState.FAILED
            }
    }

    /** The active group's roster, for building teams in Add Match. */
    /**
     * The active group's roster for building teams in Add Match: real players
     * first, then the group's guest fillers (each [Member.isGuest]). Guests come
     * back in a separate field so they never count as players elsewhere.
     */
    suspend fun getMembers(groupId: String): Result<List<Member>> =
        runCatching {
            val response = api.getMembers(groupId)
            (response.members + response.guests).map(MemberDto::toMember)
        }

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
     * Renames [groupId] and updates the cached list in place (selection is
     * unchanged). Owner/admin only — the backend rejects other members — so
     * callers should gate this on [Group.canManage].
     */
    suspend fun renameGroup(groupId: String, name: String): Result<Group> =
        runCatching { api.renameGroup(groupId, RenameGroupRequestDto(name.trim())).toGroup() }
            .onSuccess { updated ->
                _groups.update { list -> list.map { if (it.id == updated.id) updated else it } }
            }

    /**
     * Creates a shareable invite code for [groupId] (unlimited uses, no expiry).
     * Owner/admin only — the backend rejects other members — so callers should
     * gate this on [Group.canInvite].
     */
    suspend fun createInvite(groupId: String): Result<String> =
        runCatching { api.createInvite(groupId, CreateInviteRequestDto()).code }

    /**
     * Adds a person to [groupId] by email + name (owner/admin only — gate callers
     * on [Group.canManage]). Onboards someone who can't sign in yet; their account
     * is claimed automatically when they later sign in with the same email. On
     * success, [refresh] so the member count and the Add-Match roster pick them up.
     * An already-active member (`GROUP_MEMBER_EXISTS`) surfaces as
     * [MemberAlreadyExistsException] so the UI can tell it apart from a network error.
     */
    suspend fun addMember(groupId: String, email: String, name: String): Result<Member> {
        val result = runCatching { api.addMember(groupId, AddMemberRequestDto(email.trim(), name.trim())).toMember() }
            .recoverCatching { cause ->
                throw if (cause.apiErrorCode(json) == "GROUP_MEMBER_EXISTS") MemberAlreadyExistsException() else cause
            }
        if (result.isSuccess) refresh()
        return result
    }

    fun selectGroup(groupId: String) {
        _selectedGroupId.value = groupId
        // Persist so the next launch reopens this group (fire-and-forget).
        appScope.launch { selectedGroupStore.set(groupId) }
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
