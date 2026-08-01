package com.org.playboard.data.stats

import com.org.playboard.data.model.Match
import com.org.playboard.data.model.MatchPlayer
import com.org.playboard.data.model.MatchSet
import com.org.playboard.data.model.MatchTeam
import com.org.playboard.data.model.Partner
import com.org.playboard.data.model.PartnerPairing
import com.org.playboard.data.model.PlayerStats
import com.org.playboard.data.remote.PlayboardApi
import com.org.playboard.data.remote.dto.MatchPlayerDto
import com.org.playboard.data.remote.dto.MatchSetDto
import com.org.playboard.data.remote.dto.MatchSummaryDto
import com.org.playboard.data.remote.dto.MatchTeamDto
import com.org.playboard.data.remote.dto.MonthlyTrophyDto
import com.org.playboard.data.remote.dto.PartnerDto
import com.org.playboard.data.remote.dto.PartnerPairDto
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

    /** Every partner this player has had in the group, most games together first. Fetched only on expand. */
    suspend fun getPartners(groupId: String, userId: String): Result<List<Partner>> =
        runCatching { api.getPartners(groupId, userId).map(PartnerDto::toPartner) }

    /** Every pair in the group who has partnered at least once, most games together first. Fetched only on expand. */
    suspend fun getPartnerPairs(groupId: String): Result<List<PartnerPairing>> =
        runCatching { api.getGroupPartnerPairs(groupId).map(PartnerPairDto::toPartnerPairing) }

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
    recentMatches = recentMatches.map(MatchSummaryDto::toMatch),
    // mapNotNull so a malformed trophy row costs its own badge, not the whole profile.
    trophies = trophies.mapNotNull(MonthlyTrophyDto::toMonthlyTrophyOrNull),
)

private fun PartnerDto.toPartner() = Partner(
    userId = userId,
    displayName = displayName,
    photoUrl = photoUrl,
    avatarId = avatarId,
    avatarColor = avatarColor,
    gamesTogether = gamesTogether,
    winsTogether = winsTogether,
    winRate = winRate,
)

private fun PartnerPairDto.toPartnerPairing() = PartnerPairing(
    player1Id = player1Id,
    player1DisplayName = player1DisplayName,
    player1AvatarId = player1AvatarId,
    player1AvatarColor = player1AvatarColor,
    player2Id = player2Id,
    player2DisplayName = player2DisplayName,
    player2AvatarId = player2AvatarId,
    player2AvatarColor = player2AvatarColor,
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
