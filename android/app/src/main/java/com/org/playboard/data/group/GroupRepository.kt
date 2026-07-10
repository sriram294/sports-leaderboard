package com.org.playboard.data.group

import com.org.playboard.data.model.Group
import com.org.playboard.data.remote.PlayboardApi
import com.org.playboard.data.remote.dto.GroupDto
import com.org.playboard.di.AuthenticatedApi
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine

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
) {
    private val _groups = MutableStateFlow<List<Group>>(emptyList())
    val groups: StateFlow<List<Group>> = _groups.asStateFlow()

    private val _selectedGroupId = MutableStateFlow<String?>(null)

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

    fun selectGroup(groupId: String) {
        _selectedGroupId.value = groupId
    }
}

private fun GroupDto.toGroup() = Group(
    id = id,
    name = name,
    avatarColor = avatarColor,
    memberCount = memberCount,
    matchCount = matchCount,
)
