package com.org.playboard.data.model

/** A member of the active group — the roster used to build teams in Add Match. */
data class Member(
    val id: String,
    val displayName: String,
    val photoUrl: String?,
    val avatarId: String?,
    val avatarColor: String,
    val role: String,
) {
    /**
     * A per-group filler player for one-off non-members. Selectable when
     * building a match, but excluded server-side from the leaderboard, stats,
     * and the group's player count.
     */
    val isGuest: Boolean get() = role == "guest"
}
