package com.org.playboard.data.model

/** A play group the signed-in user belongs to, as shown in the group switcher. */
data class Group(
    val id: String,
    val name: String,
    val avatarColor: String,
    val memberCount: Int,
    val matchCount: Int,
    val myRole: String,
) {
    /**
     * Whether the signed-in user may create invites for this group. The backend
     * restricts `POST /groups/{id}/invites` to owners and admins, so the UI
     * gates the "Invite players" action the same way.
     */
    val canInvite: Boolean get() = myRole == "owner" || myRole == "admin"

    /**
     * Whether the signed-in user may manage this group (e.g. rename it). The
     * backend restricts `PATCH /groups/{id}` to owners and admins, so the UI
     * gates the edit-name action the same way.
     */
    val canManage: Boolean get() = myRole == "owner" || myRole == "admin"
}
