package com.org.playboard.data.leaderboard

import com.org.playboard.data.model.PlayerRanking
import com.org.playboard.data.remote.PlayboardApi
import com.org.playboard.data.remote.dto.LeaderboardEntryDto
import com.org.playboard.di.AuthenticatedApi
import javax.inject.Inject
import javax.inject.Singleton

/** Default threshold used when the backend predates ratings and sends none. */
private const val DEFAULT_MIN_GAMES_TO_RANK = 1

/**
 * A group's leaderboard: the rows plus the group-level games threshold they were
 * partitioned on. [minGamesToRank] is what turns a row's `gamesPlayed` into "N more to
 * rank", so it has to travel with the rows rather than being re-derived per row.
 */
data class Leaderboard(
    val rankings: List<PlayerRanking>,
    val minGamesToRank: Int = DEFAULT_MIN_GAMES_TO_RANK,
)

/**
 * Fetches a group's leaderboard. Rankings arrive server-sorted
 * (rating desc, then points difference desc, then wins desc), with provisional
 * players already placed after the ranked ones.
 */
@Singleton
class LeaderboardRepository @Inject constructor(
    @AuthenticatedApi private val api: PlayboardApi,
) {
    /**
     * @param from,to ISO-8601 instants bounding a calendar window (This Month);
     *   omit both (null) for the all-time ranking.
     */
    suspend fun getLeaderboard(
        groupId: String,
        from: String? = null,
        to: String? = null,
    ): Result<Leaderboard> =
        runCatching {
            val response = api.getLeaderboard(groupId, from, to)
            Leaderboard(
                rankings = response.rankings.map(LeaderboardEntryDto::toPlayerRanking),
                minGamesToRank = response.minGamesToRank ?: DEFAULT_MIN_GAMES_TO_RANK,
            )
        }
}

private fun LeaderboardEntryDto.toPlayerRanking() = PlayerRanking(
    rank = rank,
    userId = userId,
    displayName = displayName,
    photoUrl = photoUrl,
    avatarId = avatarId,
    avatarColor = avatarColor,
    gamesPlayed = gamesPlayed,
    wins = wins,
    losses = losses,
    pointsFor = pointsFor,
    pointsAgainst = pointsAgainst,
    winRate = winRate,
    currentStreak = currentStreak,
    bestStreak = bestStreak,
    rating = rating,
    // A pre-rating backend sends no rating and knows nothing of thresholds, so nobody is
    // provisional against it — the UI shows win% and the old ordering rather than
    // marking the whole board unranked.
    provisional = rating != null && provisional,
)
