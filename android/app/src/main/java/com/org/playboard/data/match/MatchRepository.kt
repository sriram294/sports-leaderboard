package com.org.playboard.data.match

import com.org.playboard.data.group.GroupRepository
import com.org.playboard.data.remote.PlayboardApi
import com.org.playboard.data.remote.apiErrorCode
import com.org.playboard.data.remote.dto.RecordMatchRequestDto
import com.org.playboard.data.remote.dto.SetInputDto
import com.org.playboard.data.remote.dto.TeamInputDto
import com.org.playboard.di.AuthenticatedApi
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json

/** Rejected by the backend's match validation (`422`). */
sealed class RecordMatchException(message: String) : Exception(message) {
    class InvalidTeams : RecordMatchException("MATCH_INVALID_TEAMS")
    class InvalidScores : RecordMatchException("MATCH_INVALID_SCORES")
}

/** Records matches for a group. One recorded match changes leaderboard/stats,
 *  so success bumps [GroupRepository.dataRevision] to prompt the Board to refresh. */
@Singleton
class MatchRepository @Inject constructor(
    @AuthenticatedApi private val api: PlayboardApi,
    private val groupRepository: GroupRepository,
    private val json: Json,
) {
    /**
     * @param sets ordered (team1, team2) score pairs, set 1 first.
     * @param winningTeamNo 1 or 2.
     */
    suspend fun recordMatch(
        groupId: String,
        team1PlayerIds: List<String>,
        team2PlayerIds: List<String>,
        sets: List<Pair<Int, Int>>,
        winningTeamNo: Int,
    ): Result<Unit> =
        runCatching {
            val request = RecordMatchRequestDto(
                playedAt = Instant.now().toString(),
                teams = listOf(
                    TeamInputDto(teamNo = 1, playerIds = team1PlayerIds),
                    TeamInputDto(teamNo = 2, playerIds = team2PlayerIds),
                ),
                sets = sets.mapIndexed { index, (t1, t2) ->
                    SetInputDto(setNo = index + 1, team1Score = t1, team2Score = t2)
                },
                winningTeamNo = winningTeamNo,
            )
            api.recordMatch(groupId, request)
            Unit
        }
            .onSuccess { groupRepository.notifyMatchesChanged() }
            .recoverCatching { cause ->
                throw when (cause.apiErrorCode(json)) {
                    "MATCH_INVALID_TEAMS" -> RecordMatchException.InvalidTeams()
                    "MATCH_INVALID_SCORES" -> RecordMatchException.InvalidScores()
                    else -> cause
                }
            }
}
