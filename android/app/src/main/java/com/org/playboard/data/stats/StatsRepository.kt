package com.org.playboard.data.stats

import com.org.playboard.data.model.BestPartner
import com.org.playboard.data.model.Match
import com.org.playboard.data.model.MatchPlayer
import com.org.playboard.data.model.MatchSet
import com.org.playboard.data.model.MatchTeam
import com.org.playboard.data.model.PlayerStats
import com.org.playboard.data.remote.PlayboardApi
import com.org.playboard.data.remote.dto.BestPartnerDto
import com.org.playboard.data.remote.dto.MatchPlayerDto
import com.org.playboard.data.remote.dto.MatchSetDto
import com.org.playboard.data.remote.dto.MatchSummaryDto
import com.org.playboard.data.remote.dto.MatchTeamDto
import com.org.playboard.data.remote.dto.MonthlyTrophyDto
import com.org.playboard.data.remote.dto.PlayerStatsDto
import com.org.playboard.data.trophy.toMonthlyTrophyOrNull
import com.org.playboard.di.AuthenticatedApi
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fetches a player's per-group stats for the Profile tab
 * (docs/requirements/05-profile.md). Same endpoint powers own stats and,
 * later, a leaderboard player's stats — the caller passes the `userId`.
 */
@Singleton
class StatsRepository @Inject constructor(
    @AuthenticatedApi private val api: PlayboardApi,
) {
    suspend fun getPlayerStats(groupId: String, userId: String): Result<PlayerStats> =
        runCatching { api.getPlayerStats(groupId, userId).toStats() }

    /**
     * The local calendar days on which the player was in a match, within `[from, to)`.
     * The backend returns UTC match instants; we bucket them into device-local days so
     * the calendar matches how the Matches list / form bar group by day.
     */
    suspend fun getPlayerAttendance(
        groupId: String,
        userId: String,
        from: String,
        to: String,
    ): Result<Set<LocalDate>> =
        runCatching {
            val zone = ZoneId.systemDefault()
            api.getPlayerAttendance(groupId, userId, from, to).playedAt
                .mapTo(mutableSetOf()) { Instant.parse(it).atZone(zone).toLocalDate() }
        }
}

private fun PlayerStatsDto.toStats() = PlayerStats(
    userId = userId,
    displayName = displayName,
    photoUrl = photoUrl,
    avatarId = avatarId,
    avatarColor = avatarColor,
    matchesPlayed = matchesPlayed,
    wins = wins,
    losses = losses,
    pointsFor = pointsFor,
    pointsAgainst = pointsAgainst,
    winRate = winRate,
    currentStreak = currentStreak,
    bestStreak = bestStreak,
    bestPartner = bestPartner?.toBestPartner(),
    recentMatches = recentMatches.map(MatchSummaryDto::toMatch),
    // mapNotNull so a malformed trophy row costs its own badge, not the whole profile.
    trophies = trophies.mapNotNull(MonthlyTrophyDto::toMonthlyTrophyOrNull),
)

private fun BestPartnerDto.toBestPartner() = BestPartner(
    userId = userId,
    displayName = displayName,
    photoUrl = photoUrl,
    avatarId = avatarId,
    avatarColor = avatarColor,
    gamesTogether = gamesTogether,
    winsTogether = winsTogether,
    winRate = winRate,
)

private fun MatchSummaryDto.toMatch() = Match(
    id = id,
    playedAt = Instant.parse(playedAt),
    teams = teams.map(MatchTeamDto::toTeam),
    sets = sets.map(MatchSetDto::toSet),
)

private fun MatchTeamDto.toTeam() = MatchTeam(
    teamNo = teamNo,
    isWinner = isWinner,
    players = players.map(MatchPlayerDto::toPlayer),
)

private fun MatchPlayerDto.toPlayer() = MatchPlayer(
    userId = userId,
    displayName = displayName,
    avatarColor = avatarColor,
    photoUrl = photoUrl,
    avatarId = avatarId,
)

private fun MatchSetDto.toSet() = MatchSet(setNo = setNo, team1Score = team1Score, team2Score = team2Score)
