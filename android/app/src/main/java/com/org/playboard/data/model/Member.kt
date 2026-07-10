package com.org.playboard.data.model

/** A member of the active group — the roster used to build teams in Add Match. */
data class Member(
    val id: String,
    val displayName: String,
    val photoUrl: String?,
    val avatarColor: String,
    val role: String,
)
