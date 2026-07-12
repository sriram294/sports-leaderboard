package com.org.playboard.service.stats;

import com.org.playboard.common.ApiException;
import com.org.playboard.dto.match.MatchSummaryDto;
import com.org.playboard.dto.stats.BestPartnerDto;
import com.org.playboard.dto.stats.LeaderboardEntryDto;
import com.org.playboard.dto.stats.LeaderboardResponse;
import com.org.playboard.dto.stats.PlayerStatsDto;
import com.org.playboard.entity.group.GroupMember;
import com.org.playboard.entity.group.GroupRole;
import com.org.playboard.entity.group.MemberStatus;
import com.org.playboard.entity.stats.MemberStats;
import com.org.playboard.entity.user.User;
import com.org.playboard.repository.group.GroupMemberRepository;
import com.org.playboard.repository.match.MatchParticipantRepository;
import com.org.playboard.repository.match.MatchParticipantRepository.PartnerRow;
import com.org.playboard.repository.stats.MemberStatsRepository;
import com.org.playboard.service.group.GroupMembershipGuard;
import com.org.playboard.service.match.MatchService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StatsQueryService {

    private static final int RECENT_MATCHES_LIMIT = 5;

    private final GroupMembershipGuard membershipGuard;
    private final GroupMemberRepository groupMemberRepository;
    private final MemberStatsRepository memberStatsRepository;
    private final MatchParticipantRepository matchParticipantRepository;
    private final MatchService matchService;

    public StatsQueryService(
            GroupMembershipGuard membershipGuard,
            GroupMemberRepository groupMemberRepository,
            MemberStatsRepository memberStatsRepository,
            MatchParticipantRepository matchParticipantRepository,
            MatchService matchService) {
        this.membershipGuard = membershipGuard;
        this.groupMemberRepository = groupMemberRepository;
        this.memberStatsRepository = memberStatsRepository;
        this.matchParticipantRepository = matchParticipantRepository;
        this.matchService = matchService;
    }

    @Transactional(readOnly = true)
    public LeaderboardResponse getLeaderboard(UUID groupId, UUID callerId) {
        membershipGuard.requireActiveMember(groupId, callerId);

        List<LeaderboardEntryDto> rankings = new ArrayList<>();
        int rank = 1;
        for (MemberStats stats : memberStatsRepository.findLeaderboard(groupId)) {
            if (stats.getMatchesPlayed() == 0) {
                continue;
            }
            User user = stats.getUser();
            rankings.add(new LeaderboardEntryDto(
                    rank++,
                    user.getId(),
                    user.getDisplayName(),
                    user.getPhotoUrl(),
                    user.getAvatarColor(),
                    stats.getMatchesPlayed(),
                    stats.getWins(),
                    stats.getLosses(),
                    stats.getPointsFor(),
                    stats.getWinRate(),
                    stats.getCurrentStreak(),
                    stats.getBestStreak()));
        }
        return new LeaderboardResponse(rankings);
    }

    @Transactional(readOnly = true)
    public PlayerStatsDto getPlayerStats(UUID groupId, UUID targetUserId, UUID callerId) {
        membershipGuard.requireActiveMember(groupId, callerId);
        GroupMember target = groupMemberRepository
                .findByGroupIdAndUserId(groupId, targetUserId)
                .filter(m -> m.getStatus() == MemberStatus.ACTIVE && m.getRole() != GroupRole.GUEST)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND, "MEMBER_NOT_FOUND", "Player is not a member of this group"));
        User user = target.getUser();

        MemberStats stats = memberStatsRepository
                .findByGroupIdAndUserId(groupId, targetUserId)
                .orElseGet(() -> new MemberStats(target.getGroup(), user));

        BestPartnerDto bestPartner = computeBestPartner(groupId, targetUserId);
        List<MatchSummaryDto> recentMatches = matchService.findRecentMatches(groupId, targetUserId, RECENT_MATCHES_LIMIT);

        return new PlayerStatsDto(
                user.getId(),
                user.getDisplayName(),
                user.getPhotoUrl(),
                user.getAvatarColor(),
                stats.getMatchesPlayed(),
                stats.getWins(),
                stats.getLosses(),
                stats.getPointsFor(),
                stats.getPointsAgainst(),
                stats.getWinRate() != null ? stats.getWinRate() : BigDecimal.ZERO,
                stats.getCurrentStreak(),
                stats.getBestStreak(),
                bestPartner,
                recentMatches);
    }

    // "Best" = highest win rate together (min 1 game), tie-broken by most
    // games together. Null if the player has no completed matches with a
    // teammate yet — see api-contracts.md.
    private BestPartnerDto computeBestPartner(UUID groupId, UUID userId) {
        List<PartnerRow> rows = matchParticipantRepository.findPartnerHistory(groupId, userId);
        if (rows.isEmpty()) {
            return null;
        }

        // Guest fillers aren't real partners — leave them out of the tally so a
        // one-off guest can never surface as someone's "best partner".
        Set<UUID> guestIds = groupMemberRepository
                .findByGroupIdAndStatusAndRole(groupId, MemberStatus.ACTIVE, GroupRole.GUEST)
                .stream()
                .map(m -> m.getUser().getId())
                .collect(Collectors.toSet());

        Map<UUID, int[]> tally = new HashMap<>();
        for (PartnerRow row : rows) {
            if (guestIds.contains(row.getPartnerId())) {
                continue;
            }
            int[] counts = tally.computeIfAbsent(row.getPartnerId(), k -> new int[2]);
            counts[0]++;
            if (row.isWinner()) {
                counts[1]++;
            }
        }
        if (tally.isEmpty()) {
            return null;
        }

        UUID bestPartnerId = null;
        int[] bestCounts = null;
        for (Map.Entry<UUID, int[]> entry : tally.entrySet()) {
            int[] counts = entry.getValue();
            if (bestCounts == null || isBetterPartner(counts, bestCounts)) {
                bestPartnerId = entry.getKey();
                bestCounts = counts;
            }
        }

        User partner = groupMemberRepository
                .findByGroupIdAndUserId(groupId, bestPartnerId)
                .orElseThrow()
                .getUser();
        BigDecimal winRate = BigDecimal.valueOf(bestCounts[1])
                .divide(BigDecimal.valueOf(bestCounts[0]), 4, RoundingMode.HALF_UP);
        return new BestPartnerDto(
                partner.getId(), partner.getDisplayName(), partner.getAvatarColor(), bestCounts[0], bestCounts[1], winRate);
    }

    private boolean isBetterPartner(int[] candidate, int[] current) {
        double candidateWinRate = (double) candidate[1] / candidate[0];
        double currentWinRate = (double) current[1] / current[0];
        if (candidateWinRate != currentWinRate) {
            return candidateWinRate > currentWinRate;
        }
        return candidate[0] > current[0];
    }
}
