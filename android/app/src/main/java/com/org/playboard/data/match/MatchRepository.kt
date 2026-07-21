package com.org.playboard.data.match

import com.org.playboard.data.group.GroupRepository
import com.org.playboard.data.model.Match
import com.org.playboard.data.model.MatchDetail
import com.org.playboard.data.model.MatchEvent
import com.org.playboard.data.model.MatchPlayer
import com.org.playboard.data.model.MatchSet
import com.org.playboard.data.model.MatchTeam
import com.org.playboard.data.remote.PlayboardApi
import com.org.playboard.data.remote.apiErrorCode
import com.org.playboard.data.remote.dto.MatchDetailDto
import com.org.playboard.data.remote.dto.MatchEventDto
import com.org.playboard.data.remote.dto.MatchPlayerDto
import com.org.playboard.data.remote.dto.MatchSetDto
import com.org.playboard.data.remote.dto.MatchSummaryDto
import com.org.playboard.data.remote.dto.MatchTeamDto
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

/** Default match-list page size — mirrors the backend contract's `limit=20`. */
private const val PAGE_SIZE = 20

/**
 * One cursor-paginated page of matches (newest first). [nextCursor] is `null` when there
 * are no more matches to load — see `GET /groups/{id}/matches` in api-contracts.md.
 */
data class MatchPage(val matches: List<Match>, val nextCursor: String?)

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
        runCatching { api.recordMatch(groupId, buildRequest(team1PlayerIds, team2PlayerIds, sets, winningTeamNo)); Unit }
            .onSuccess { groupRepository.notifyMatchesChanged() }
            .recoverMatchValidation()

    /**
     * Full-replace edit of an existing match (`PATCH`), same request shape as
     * record. Recomputes stats server-side, so bump the data revision on success.
     * @param sets ordered (team1, team2) score pairs, set 1 first.
     * @param winningTeamNo 1 or 2.
     * @param playedAt the match's original played time, round-tripped from the loaded
     *   detail. The endpoint overwrites `played_at` with whatever it receives, so sending
     *   "now" here would re-date the match to the moment it was edited and move it under
     *   today's heading in the Matches log.
     */
    suspend fun editMatch(
        groupId: String,
        matchId: String,
        team1PlayerIds: List<String>,
        team2PlayerIds: List<String>,
        sets: List<Pair<Int, Int>>,
        winningTeamNo: Int,
        playedAt: Instant,
    ): Result<Unit> =
        runCatching {
            api.editMatch(groupId, matchId, buildRequest(team1PlayerIds, team2PlayerIds, sets, winningTeamNo, playedAt))
            Unit
        }
            .onSuccess { groupRepository.notifyMatchesChanged() }
            .recoverMatchValidation()

    private fun buildRequest(
        team1PlayerIds: List<String>,
        team2PlayerIds: List<String>,
        sets: List<Pair<Int, Int>>,
        winningTeamNo: Int,
        playedAt: Instant = Instant.now(),
    ) = RecordMatchRequestDto(
        playedAt = playedAt.toString(),
        teams = listOf(
            TeamInputDto(teamNo = 1, playerIds = team1PlayerIds),
            TeamInputDto(teamNo = 2, playerIds = team2PlayerIds),
        ),
        sets = sets.mapIndexed { index, (t1, t2) ->
            SetInputDto(setNo = index + 1, team1Score = t1, team2Score = t2)
        },
        winningTeamNo = winningTeamNo,
    )

    /** Maps the backend's `422` validation codes to typed exceptions. */
    private fun Result<Unit>.recoverMatchValidation(): Result<Unit> =
        recoverCatching { cause ->
            throw when (cause.apiErrorCode(json)) {
                "MATCH_INVALID_TEAMS" -> RecordMatchException.InvalidTeams()
                "MATCH_INVALID_SCORES" -> RecordMatchException.InvalidScores()
                else -> cause
            }
        }

    /**
     * One page of the group's matches, newest first. Pass [cursor] = `null` for the
     * first page; subsequent pages use the previous page's [MatchPage.nextCursor].
     * @param limit page size (defaults to [PAGE_SIZE]).
     * @param mine when `true`, scopes the page to matches the caller played in.
     */
    suspend fun getMatches(
        groupId: String,
        cursor: String? = null,
        limit: Int = PAGE_SIZE,
        mine: Boolean = false,
    ): Result<MatchPage> =
        runCatching {
            api.getMatches(groupId, cursor, limit, mine.takeIf { it }).let {
                MatchPage(it.matches.map(MatchSummaryDto::toMatch), it.nextCursor)
            }
        }

    suspend fun getMatchDetail(groupId: String, matchId: String): Result<MatchDetail> =
        runCatching { api.getMatchDetail(groupId, matchId).toDetail() }

    /** Deletes a match; recomputes stats server-side, so bump the data revision. */
    suspend fun deleteMatch(groupId: String, matchId: String): Result<Unit> =
        runCatching { api.deleteMatch(groupId, matchId) }
            .onSuccess { groupRepository.notifyMatchesChanged() }
}

private fun MatchSummaryDto.toMatch() = Match(
    id = id,
    playedAt = Instant.parse(playedAt),
    teams = teams.map(MatchTeamDto::toTeam),
    sets = sets.map(MatchSetDto::toSet),
)

private fun MatchDetailDto.toDetail() = MatchDetail(
    id = id,
    playedAt = Instant.parse(playedAt),
    teams = teams.map(MatchTeamDto::toTeam),
    sets = sets.map(MatchSetDto::toSet),
    recordedByUserId = recordedBy.userId,
    recordedByName = recordedBy.displayName,
    recordedAt = Instant.parse(recordedAt),
    events = events.map(MatchEventDto::toEvent),
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

private fun MatchEventDto.toEvent() = MatchEvent(
    displayName = displayName,
    action = action,
    createdAt = Instant.parse(createdAt),
)
