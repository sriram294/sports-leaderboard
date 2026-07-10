package com.org.playboard.data.model

/** Signed-in user's identity, as needed by the UI layer across screens. */
data class UserSession(
    val id: String,
    val displayName: String,
    val email: String,
    val photoUrl: String?,
    val avatarColor: String,
)
