package com.org.playboard.data.model

/** A play group the signed-in user belongs to, as shown in the group switcher. */
data class Group(
    val id: String,
    val name: String,
    val avatarColor: String,
    val memberCount: Int,
    val matchCount: Int,
)
