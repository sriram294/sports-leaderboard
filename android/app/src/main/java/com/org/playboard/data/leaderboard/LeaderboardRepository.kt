package com.org.playboard.data.leaderboard

import com.org.playboard.data.model.PlayerRanking
import com.org.playboard.data.remote.PlayboardApi
import com.org.playboard.data.remote.dto.LeaderboardEntryDto
import com.org.playboard.di.AuthenticatedApi
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fetches a group's leaderboard. Rankings arrive server-sorted
 * (win rate desc, then points difference desc, then wins desc).
 */
@Singleton
class LeaderboardRepository @Inject constructor(
    @AuthenticatedApi private val api: PlayboardApi,
) {
    /**
     * @param from,to ISO-8601 instants bounding a calendar window (This Week / This Month);
     *   omit both (null) for the all-time ranking.
     */
    suspend fun getLeaderboard(
        groupId: String,
        from: String? = null,
        to: String? = null,
    ): Result<List<PlayerRanking>> =
        runCatching { api.getLeaderboard(groupId, from, to).rankings.map(LeaderboardEntryDto::toPlayerRanking) }
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
    pointsAgainst = pointsAgainst,
    winRate = winRate,
    currentStreak = currentStreak,
    bestStreak = bestStreak,
)
