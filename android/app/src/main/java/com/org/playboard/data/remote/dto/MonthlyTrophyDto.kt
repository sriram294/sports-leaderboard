package com.org.playboard.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * A month a player topped the group's leaderboard — from `GET /groups/{groupId}/trophies`
 * and embedded in [PlayerStatsDto].
 *
 * Every field is defaulted so the app keeps deserializing against a backend that predates
 * trophies: during a rollout the whole list is simply absent, and the shelf renders empty
 * rather than the profile failing to load.
 */
@Serializable
data class MonthlyTrophyDto(
    /** ISO `YYYY-MM`. Not a full date — the award belongs to the month, not a day in it. */
    val month: String = "",
    val userId: String = "",
    val displayName: String = "",
    val photoUrl: String? = null,
    val avatarId: String? = null,
    val avatarColor: String = "#000000",
    // The winning stats as they stood when the month closed. Nullable rather than
    // defaulted to 0: a null means "the backend didn't record it", while 0 games would be
    // a factual claim about the month, and the two must stay distinguishable.
    val rating: Double? = null,
    val gamesPlayed: Int? = null,
    val wins: Int? = null,
)
