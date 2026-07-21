package com.org.playboard.data.trophy

import com.org.playboard.data.model.MonthlyTrophy
import com.org.playboard.data.remote.PlayboardApi
import com.org.playboard.data.remote.dto.MonthlyTrophyDto
import com.org.playboard.di.AuthenticatedApi
import java.time.YearMonth
import javax.inject.Inject
import javax.inject.Singleton

/** How many months the Stats roll of honour shows. */
const val GROUP_TROPHY_LIMIT = 6

/**
 * Fetches the monthly leaderboard crowns a group has awarded.
 *
 * A player's own crowns arrive embedded in their stats payload instead — see
 * `StatsRepository` — so this exists only for the group-wide roll of honour.
 */
@Singleton
class TrophyRepository @Inject constructor(
    @AuthenticatedApi private val api: PlayboardApi,
) {
    suspend fun getGroupTrophies(
        groupId: String,
        limit: Int = GROUP_TROPHY_LIMIT,
    ): Result<List<MonthlyTrophy>> =
        runCatching {
            // mapNotNull, not map: one unparseable month drops that row rather than
            // emptying the whole roll of honour.
            api.getGroupTrophies(groupId, limit).mapNotNull(MonthlyTrophyDto::toMonthlyTrophyOrNull)
        }
}

/**
 * Internal rather than private: [com.org.playboard.data.stats.StatsRepository] maps the same
 * DTO out of the player-stats payload, and one shared mapper keeps the two from drifting.
 *
 * Rows whose `month` can't be parsed are dropped by the callers via [toMonthlyTrophyOrNull];
 * this variant assumes a well-formed value.
 */
internal fun MonthlyTrophyDto.toMonthlyTrophy() = MonthlyTrophy(
    month = YearMonth.parse(month),
    userId = userId,
    displayName = displayName,
    photoUrl = photoUrl,
    avatarId = avatarId,
    avatarColor = avatarColor,
    rating = rating,
    gamesPlayed = gamesPlayed,
    wins = wins,
)

/**
 * Null when `month` isn't a parseable `YYYY-MM`.
 *
 * Trophies are decoration on screens whose main content is stats: a malformed row must not
 * take down a profile, so callers drop the row instead of letting [YearMonth.parse] throw
 * out of the surrounding `runCatching`.
 */
internal fun MonthlyTrophyDto.toMonthlyTrophyOrNull(): MonthlyTrophy? =
    runCatching { toMonthlyTrophy() }.getOrNull()
