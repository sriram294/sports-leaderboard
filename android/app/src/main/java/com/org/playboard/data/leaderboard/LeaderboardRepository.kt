package com.org.playboard.data.leaderboard

import com.org.playboard.data.model.PlayerRanking
import com.org.playboard.data.remote.PlayboardApi
import com.org.playboard.data.remote.dto.LeaderboardEntryDto
import com.org.playboard.di.AuthenticatedApi
import javax.inject.Inject
import javax.inject.Singleton

/** Fetches a group's leaderboard. Rankings arrive server-sorted (win rate desc, then wins desc). */
@Singleton
class LeaderboardRepository @Inject constructor(
    @AuthenticatedApi private val api: PlayboardApi,
) {
    suspend fun getLeaderboard(groupId: String): Result<List<PlayerRanking>> =
        runCatching { api.getLeaderboard(groupId).rankings.map(LeaderboardEntryDto::toPlayerRanking) }
}

private fun LeaderboardEntryDto.toPlayerRanking() = PlayerRanking(
    rank = rank,
    userId = userId,
    displayName = displayName,
    photoUrl = photoUrl,
    avatarColor = avatarColor,
    gamesPlayed = gamesPlayed,
    wins = wins,
    losses = losses,
    pointsFor = pointsFor,
    winRate = winRate,
    currentStreak = currentStreak,
    bestStreak = bestStreak,
)
