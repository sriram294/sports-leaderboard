package com.org.playboard.service.stats;

import com.org.playboard.entity.group.GroupRole;
import com.org.playboard.entity.group.GroupMember;
import com.org.playboard.entity.group.MemberStatus;
import com.org.playboard.entity.match.Match;
import com.org.playboard.entity.match.MatchParticipant;
import com.org.playboard.entity.match.MatchTeam;
import com.org.playboard.entity.user.User;
import com.org.playboard.repository.group.GroupMemberRepository;
import com.org.playboard.repository.match.MatchParticipantRepository;
import com.org.playboard.repository.match.MatchRepository;
import com.org.playboard.repository.match.MatchTeamRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Replays the selected period in deterministic (played_at, match_id) order. */
@Service
public class TeamRatingService {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(TeamRatingService.class);
    public static final String ALGORITHM_VERSION = "team-v2";
    public static final String PERIOD = "cumulative";

    private final GroupMemberRepository members;
    private final MatchRepository matches;
    private final MatchTeamRepository teams;
    private final MatchParticipantRepository participants;

    public TeamRatingService(GroupMemberRepository members, MatchRepository matches,
            MatchTeamRepository teams, MatchParticipantRepository participants) {
        this.members = members;
        this.matches = matches;
        this.teams = teams;
        this.participants = participants;
    }

    public record PlayerRating(double mean, double sigma, int games, int wins, int uniquePartners,
            BigDecimal maxPartnerShare, boolean qualified, String provisionalReason,
            boolean limitedPartnerVariety) {
        public BigDecimal displayRating() {
            return TeamRatingService.displayRating(new TeamRatingCalculator.Rating(mean, sigma));
        }
    }

    /** The 0-100, one-decimal value exposed by the team-v2 leaderboard. */
    public static BigDecimal displayRating(TeamRatingCalculator.Rating rating) {
        return BigDecimal.valueOf(unroundedDisplayRating(rating)).setScale(1, RoundingMode.HALF_UP);
    }

    /** The unrounded 0-100 display value, used when deriving match deltas. */
    public static double unroundedDisplayRating(TeamRatingCalculator.Rating rating) {
        double score = rating.mean() - 3 * rating.sigma();
        return 100d / (1d + Math.exp(-(score - 17d) / 3d));
    }

    public Map<UUID, PlayerRating> replay(UUID groupId, Instant from, Instant to,
            Map<UUID, User> eligible) {
        return replay(groupId, from, to, eligible, from);
    }

    /** Replays ratings from {@code from}, while collecting qualification statistics from a separate window. */
    public Map<UUID, PlayerRating> replay(UUID groupId, Instant from, Instant to,
            Map<UUID, User> eligible, Instant statisticsFrom) {
        Set<UUID> guestIds = new HashSet<>();
        Set<UUID> allRegularIds = new HashSet<>();
        for (GroupMember member : members.findByGroupId(groupId)) {
            if (member.getRole() == GroupRole.GUEST) {
                guestIds.add(member.getUser().getId());
            } else {
                allRegularIds.add(member.getUser().getId());
            }
        }
        Map<UUID, TeamRatingCalculator.Rating> ratings = new HashMap<>();
        Map<UUID, Integer> games = new HashMap<>();
        Map<UUID, Integer> wins = new HashMap<>();
        Map<UUID, Map<UUID, Integer>> partnerGames = new HashMap<>();
        List<Match> ratingMatches = matches.findRatingMatches(groupId, from, to);
        List<UUID> matchIds = ratingMatches.stream().map(Match::getId).toList();
        Map<UUID, List<MatchTeam>> teamsByMatch = new HashMap<>();
        Map<UUID, List<com.org.playboard.entity.match.MatchParticipant>> participantsByTeam = new HashMap<>();
        if (!matchIds.isEmpty()) {
            List<MatchTeam> allTeams = teams.findByMatchIdInOrderByMatchIdAscTeamNoAsc(matchIds);
            allTeams.forEach(t -> teamsByMatch.computeIfAbsent(t.getMatch().getId(), k -> new ArrayList<>()).add(t));
            List<UUID> teamIds = allTeams.stream().map(MatchTeam::getId).toList();
            if (!teamIds.isEmpty()) participants.findByMatchTeamIdIn(teamIds)
                    .forEach(p -> participantsByTeam.computeIfAbsent(p.getMatchTeam().getId(), k -> new ArrayList<>()).add(p));
        }
        for (Match match : ratingMatches) {
            boolean inStatisticsWindow = !match.getPlayedAt().isBefore(statisticsFrom);
            List<MatchTeam> matchTeams = teamsByMatch.getOrDefault(match.getId(), List.of());
            if (matchTeams.size() != 2 || matchTeams.get(0).isWinner() == matchTeams.get(1).isWinner()) {
                log.warn("Skipping malformed match {} while replaying team-v2 ratings", match.getId());
                continue;
            }
            List<TeamRatingCalculator.Team> result = new ArrayList<>();
            boolean malformed = false;
            Set<UUID> seenPlayers = new HashSet<>();
            for (MatchTeam team : matchTeams) {
                List<TeamRatingCalculator.Participant> roster = participantsByTeam.getOrDefault(team.getId(), List.of()).stream()
                        .map(p -> new TeamRatingCalculator.Participant(p.getUser().getId(), guestIds.contains(p.getUser().getId())))
                        .toList();
                if (roster.isEmpty()) malformed = true;
                for (TeamRatingCalculator.Participant player : roster) {
                    if (!seenPlayers.add(player.userId())) malformed = true;
                }
                result.add(new TeamRatingCalculator.Team(roster, team.isWinner()));
            }
            if (malformed) {
                log.warn("Skipping malformed match {} while replaying team-v2 ratings", match.getId());
                continue;
            }
            for (TeamRatingCalculator.Team team : result) {
                List<UUID> regular = team.players().stream().map(TeamRatingCalculator.Participant::userId)
                        .filter(allRegularIds::contains).toList();
                for (UUID player : regular) {
                    if (!inStatisticsWindow) {
                        ratings.putIfAbsent(player, new TeamRatingCalculator.Rating());
                        continue;
                    }
                    games.merge(player, 1, Integer::sum);
                    if (team.winner()) wins.merge(player, 1, Integer::sum);
                    for (UUID partner : regular) if (!player.equals(partner) && eligible.containsKey(partner)) {
                        partnerGames.computeIfAbsent(player, k -> new HashMap<>()).merge(partner, 1, Integer::sum);
                    }
                    ratings.putIfAbsent(player, new TeamRatingCalculator.Rating());
                }
            }
            ratings = new HashMap<>(TeamRatingCalculator.update(ratings, result));
        }
        int threshold = LeaderboardRanker.minGamesToRank(eligible.keySet().stream()
                .map(id -> games.getOrDefault(id, 0)).toList());
        int requiredPartners = Math.min(3, Math.max(0, eligible.size() - 1));
        if (requiredPartners == 0 && eligible.size() > 1) requiredPartners = 1;
        BigDecimal cap = BigDecimal.valueOf(requiredPartners >= 3 ? .60 : requiredPartners == 2 ? .75 : 1.0);
        Map<UUID, PlayerRating> out = new HashMap<>();
        for (UUID id : eligible.keySet()) {
            int played = games.getOrDefault(id, 0);
            Map<UUID, Integer> partners = partnerGames.getOrDefault(id, Map.of());
            int unique = partners.size();
            int max = partners.values().stream().max(Comparator.naturalOrder()).orElse(0);
            BigDecimal share = played == 0 ? BigDecimal.ZERO : BigDecimal.valueOf(max)
                    .divide(BigDecimal.valueOf(played), 4, RoundingMode.HALF_UP);
            boolean limited = unique < requiredPartners || share.compareTo(cap) > 0;
            boolean qualified = played >= threshold;
            String reason = qualified ? null : "games";
            TeamRatingCalculator.Rating rating = ratings.getOrDefault(id, new TeamRatingCalculator.Rating());
            out.put(id, new PlayerRating(rating.mean(), rating.sigma(), played, wins.getOrDefault(id, 0), unique, share, qualified, reason, limited));
        }
        return out;
    }
}
