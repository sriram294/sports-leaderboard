package com.org.playboard.ui.group

import com.org.playboard.data.model.Group
import com.org.playboard.data.model.Member

/**
 * State for the group-management drill-down (owner/admin). A `null` [selectedGroupId] shows
 * the managed-groups list; a set id shows that group's detail (members + session window).
 */
data class GroupManagementUiState(
    val currentUserId: String? = null,
    /** Groups the signed-in user manages (owner/admin). */
    val managedGroups: List<Group> = emptyList(),
    val selectedGroupId: String? = null,
    /** Real (non-guest) members of the selected group. */
    val members: List<Member> = emptyList(),
    val isLoadingMembers: Boolean = false,
    val membersFailed: Boolean = false,
    /** A remove/role/session/add/invite action is in flight. */
    val busy: Boolean = false,
    /** A retryable action failure to surface, cleared on the next attempt. */
    val actionError: String? = null,
    /** A freshly created invite code to show in a dialog; `null` when none. */
    val inviteCode: String? = null,
) {
    val selectedGroup: Group? get() = managedGroups.firstOrNull { it.id == selectedGroupId }

    /** The signed-in user may change roles only in a group they own. */
    fun canChangeRoles(group: Group): Boolean = group.isOwner

    /**
     * Whether [member] can be removed by the viewer in [group]: never the owner, guests, or
     * self; an admin viewer can remove only plain members (owner can remove admins too).
     */
    fun canRemove(group: Group, member: Member): Boolean {
        if (member.id == currentUserId || member.role == "owner" || member.isGuest) return false
        return group.isOwner || member.role == "member"
    }
}
