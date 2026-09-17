package com.org.playboard.service.stats;

import com.org.playboard.dto.match.RatingChangeDto;
import com.org.playboard.entity.group.GroupMember;
import com.org.playboard.entity.group.GroupRole;
import com.org.playboard.entity.match.Match;
import com.org.playboard.entity.match.MatchParticipant;
import com.org.playboard.entity.match.MatchTeam;
import com.org.playboard.repository.group.GroupMemberRepository;
import com.org.playboard.repository.match.MatchParticipantRepository;
import com.org.playboard.repository.match.MatchRepository;
import com.org.playboard.repository.match.MatchTeamRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Derives per-participant, two-decimal rating changes by replaying current group history on demand. */
@Service
public class MatchRatingChangeService {
    private static final int DELTA_SCALE = 2;
    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(MatchRatingChangeService.class);

    private final GroupMemberRepository members;
    private final MatchRepository matches;
    private final MatchTeamRepository teams;
    private final MatchParticipantRepository participants;
    private final boolean teamV2Enabled;

    public MatchRatingChangeService(
            GroupMemberRepository members,
            MatchRepository matches,
            MatchTeamRepository teams,
            MatchParticipantRepository participants,
            @Value("${playboard.ratings.team-v2-enabled:true}") boolean teamV2Enabled) {
        this.members = members;
        this.matches = matches;
        this.teams = teams;
        this.participants = participants;
        this.teamV2Enabled = teamV2Enabled;
    }

    public List<RatingChangeDto> calculate(Match target) {
        UUID groupId = target.getGroup().getId();
        Set<UUID> guestIds = new HashSet<>();
        for (GroupMember member : members.findByGroupId(groupId)) {
            if (member.getRole() == GroupRole.GUEST) guestIds.add(member.getUser().getId());
        }

        List<Match> history = matches.findRatingMatchesThrough(groupId, target.getPlayedAt(), target.getId());
        List<UUID> matchIds = history.stream().map(Match::getId).toList();
        Map<UUID, List<MatchTeam>> teamsByMatch = new HashMap<>();
        Map<UUID, List<MatchParticipant>> participantsByTeam = new HashMap<>();
        if (!matchIds.isEmpty()) {
            List<MatchTeam> allTeams = teams.findByMatchIdInOrderByMatchIdAscTeamNoAsc(matchIds);
            allTeams.forEach(team -> teamsByMatch
                    .computeIfAbsent(team.getMatch().getId(), ignored -> new ArrayList<>()).add(team));
            List<UUID> teamIds = allTeams.stream().map(MatchTeam::getId).toList();
            if (!teamIds.isEmpty()) {
                participants.findByMatchTeamIdIn(teamIds).forEach(participant -> participantsByTeam
                        .computeIfAbsent(participant.getMatchTeam().getId(), ignored -> new ArrayList<>())
                        .add(participant));
            }
        }

        Map<UUID, TeamRatingCalculator.Rating> ratings = new HashMap<>();
        Map<UUID, Integer> games = new HashMap<>();
        Map<UUID, Integer> wins = new HashMap<>();
        for (Match match : history) {
            List<MatchTeam> matchTeams = teamsByMatch.getOrDefault(match.getId(), List.of());
            List<TeamRatingCalculator.Team> result = ratingTeams(matchTeams, participantsByTeam, guestIds);
            boolean malformed = result == null;
            if (malformed) {
                log.warn("Skipping malformed match {} while deriving rating changes", match.getId());
            }

            if (match.getId().equals(target.getId())) {
                return changesForTarget(matchTeams, participantsByTeam, guestIds, result, ratings, games, wins);
            }
            if (!malformed) {
                if (teamV2Enabled) {
                    ratings = new HashMap<>(TeamRatingCalculator.update(ratings, result));
                } else {
                    applyWilson(result, games, wins);
                }
            }
        }
        return List.of();
    }

    private List<RatingChangeDto> changesForTarget(
            List<MatchTeam> matchTeams,
            Map<UUID, List<MatchParticipant>> participantsByTeam,
            Set<UUID> guestIds,
            List<TeamRatingCalculator.Team> result,
            Map<UUID, TeamRatingCalculator.Rating> ratings,
            Map<UUID, Integer> games,
            Map<UUID, Integer> wins) {
        Map<UUID, BigDecimal> before = new HashMap<>();
        Map<UUID, BigDecimal> wilsonDeltas = new HashMap<>();
        for (MatchTeam team : matchTeams) {
            for (MatchParticipant participant : participantsByTeam.getOrDefault(team.getId(), List.of())) {
                UUID userId = participant.getUser().getId();
                if (!guestIds.contains(userId)) {
                    before.put(userId, ratingValue(userId, ratings, games, wins));
                    if (!teamV2Enabled && result != null) {
                        wilsonDeltas.put(userId, MatchRatingDeltaCalculator.delta(
                                games.getOrDefault(userId, 0), wins.getOrDefault(userId, 0), team.isWinner()));
                    }
                }
            }
        }
        if (result != null) {
            if (teamV2Enabled) ratings.putAll(TeamRatingCalculator.update(ratings, result));
            else applyWilson(result, games, wins);
        }

        List<RatingChangeDto> changes = new ArrayList<>();
        for (MatchTeam team : matchTeams) {
            for (MatchParticipant participant : participantsByTeam.getOrDefault(team.getId(), List.of())) {
                UUID userId = participant.getUser().getId();
                BigDecimal delta = guestIds.contains(userId)
                        ? null
                        : teamV2Enabled || result == null
                                ? roundDelta(ratingValue(userId, ratings, games, wins)
                                        .subtract(before.get(userId)))
                                : wilsonDeltas.get(userId);
                changes.add(new RatingChangeDto(userId, delta));
            }
        }
        return changes;
    }

    private BigDecimal ratingValue(UUID userId, Map<UUID, TeamRatingCalculator.Rating> ratings,
            Map<UUID, Integer> games, Map<UUID, Integer> wins) {
        if (teamV2Enabled) {
            return BigDecimal.valueOf(TeamRatingService.unroundedDisplayRating(
                    ratings.getOrDefault(userId, new TeamRatingCalculator.Rating())));
        }
        return LeaderboardRanker.rating(new LeaderboardRanker.RawStatRow(
                userId, games.getOrDefault(userId, 0), wins.getOrDefault(userId, 0), 0, 0, 0, 0));
    }

    private BigDecimal roundDelta(BigDecimal delta) {
        return delta.setScale(DELTA_SCALE, RoundingMode.HALF_UP);
    }

    private void applyWilson(List<TeamRatingCalculator.Team> result,
            Map<UUID, Integer> games, Map<UUID, Integer> wins) {
        for (TeamRatingCalculator.Team team : result) {
            for (TeamRatingCalculator.Participant participant : team.players()) {
                if (participant.guest()) continue;
                games.merge(participant.userId(), 1, Integer::sum);
                if (team.winner()) wins.merge(participant.userId(), 1, Integer::sum);
            }
        }
    }

    /** Mirrors TeamRatingService's malformed-match rules so detail and leaderboard replay agree. */
    private List<TeamRatingCalculator.Team> ratingTeams(List<MatchTeam> matchTeams,
            Map<UUID, List<MatchParticipant>> participantsByTeam, Set<UUID> guestIds) {
        if (matchTeams.size() != 2 || matchTeams.get(0).isWinner() == matchTeams.get(1).isWinner()) return null;
        List<TeamRatingCalculator.Team> result = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();
        for (MatchTeam team : matchTeams) {
            List<TeamRatingCalculator.Participant> roster = participantsByTeam
                    .getOrDefault(team.getId(), List.of()).stream()
                    .map(participant -> new TeamRatingCalculator.Participant(
                            participant.getUser().getId(), guestIds.contains(participant.getUser().getId())))
                    .toList();
            if (roster.isEmpty() || roster.stream().anyMatch(player -> !seen.add(player.userId()))) return null;
            result.add(new TeamRatingCalculator.Team(roster, team.isWinner()));
        }
        return result;
    }
}
