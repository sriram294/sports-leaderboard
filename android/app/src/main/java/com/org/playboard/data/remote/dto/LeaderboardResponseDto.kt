package com.org.playboard.data.remote.dto

import kotlinx.serialization.Serializable

/** Envelope of `GET /groups/{groupId}/leaderboard`. */
@Serializable
data class LeaderboardResponseDto(
    val rankings: List<LeaderboardEntryDto>,
    /**
     * Games a player needs before they rank rather than showing as provisional. A
     * group-level fact, so it's sent once here instead of on every entry; clients derive
     * "N more to rank" as `minGamesToRank - gamesPlayed`. Null against a pre-rating backend.
     */
    val minGamesToRank: Int? = null,
)
