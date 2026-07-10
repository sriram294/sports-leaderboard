package com.org.playboard.data.model

import java.time.Instant

/** A recorded doubles match in a group (the Matches log). */
data class Match(
    val id: String,
    val playedAt: Instant,
    val teams: List<MatchTeam>,
    val sets: List<MatchSet>,
) {
    val winningTeamNo: Int? get() = teams.firstOrNull { it.isWinner }?.teamNo
    fun team(teamNo: Int): MatchTeam? = teams.firstOrNull { it.teamNo == teamNo }
}

data class MatchTeam(
    val teamNo: Int,
    val isWinner: Boolean,
    val players: List<MatchPlayer>,
)

data class MatchPlayer(
    val userId: String,
    val displayName: String,
    val avatarColor: String,
    val photoUrl: String?,
)

data class MatchSet(
    val setNo: Int,
    val team1Score: Int,
    val team2Score: Int,
)

/** Full match detail: summary fields plus who recorded it and the audit log. */
data class MatchDetail(
    val id: String,
    val playedAt: Instant,
    val teams: List<MatchTeam>,
    val sets: List<MatchSet>,
    val recordedByUserId: String,
    val recordedByName: String,
    val recordedAt: Instant,
    val events: List<MatchEvent>,
) {
    fun team(teamNo: Int): MatchTeam? = teams.firstOrNull { it.teamNo == teamNo }
    val winningTeamNo: Int? get() = teams.firstOrNull { it.isWinner }?.teamNo
}

data class MatchEvent(
    val displayName: String,
    val action: String,
    val createdAt: Instant,
)
