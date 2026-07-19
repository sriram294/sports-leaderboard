package com.org.playboard.data.model

/** A play group the signed-in user belongs to, as shown in the group switcher. */
data class Group(
    val id: String,
    val name: String,
    val avatarColor: String,
    val memberCount: Int,
    val matchCount: Int,
    val myRole: String,
    /** Daily session window ("HH:mm" local), or `null` when unset. */
    val sessionStart: String? = null,
    val sessionEnd: String? = null,
) {
    /** The signed-in user owns this group — the only role that may change others' roles. */
    val isOwner: Boolean get() = myRole == "owner"

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
