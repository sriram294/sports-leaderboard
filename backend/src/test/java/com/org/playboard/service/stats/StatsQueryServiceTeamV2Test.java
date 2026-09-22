package com.org.playboard.service.stats;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.org.playboard.entity.group.GroupMember;
import com.org.playboard.entity.group.GroupRole;
import com.org.playboard.entity.group.MemberStatus;
import com.org.playboard.entity.user.User;
import com.org.playboard.repository.group.GroupMemberRepository;
import com.org.playboard.repository.match.MatchParticipantRepository;
import com.org.playboard.repository.stats.MemberStatsRepository;
import com.org.playboard.repository.stats.MonthlyStandingRepository;
import com.org.playboard.service.group.GroupMembershipGuard;
import com.org.playboard.service.match.MatchService;
import com.org.playboard.service.user.AvatarUrlResolver;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StatsQueryServiceTeamV2Test {

    @Test
    void pinnedThresholdKeepsTheTeamV2RankingPath() {
        UUID groupId = UUID.randomUUID();
        UUID playerId = UUID.randomUUID();
        Instant from = Instant.parse("2026-09-01T00:00:00Z");
        Instant to = Instant.parse("2026-09-22T00:00:00Z");

        GroupMemberRepository members = mock(GroupMemberRepository.class);
        MatchParticipantRepository participants = mock(MatchParticipantRepository.class);
        TeamRatingService teamRatings = mock(TeamRatingService.class);
        User player = mock(User.class);
        when(player.getId()).thenReturn(playerId);
        when(player.getDisplayName()).thenReturn("Player");
        GroupMember member = mock(GroupMember.class);
        when(member.getUser()).thenReturn(player);
        when(member.getRole()).thenReturn(GroupRole.MEMBER);
        when(members.findByGroupIdAndStatus(groupId, MemberStatus.ACTIVE)).thenReturn(List.of(member));
        when(teamRatings.replay(eq(groupId), eq(Instant.EPOCH), eq(to), anyMap(), eq(from)))
                .thenReturn(Map.of(playerId, new TeamRatingService.PlayerRating(
                        25, 8.333333333333333, 1, 1, 0, BigDecimal.ZERO, false, "games", false)));
        when(participants.findWindowedStreaks(groupId, from, to)).thenReturn(List.of());
        when(participants.findRecentFormForGroup(groupId, from, to, 10)).thenReturn(List.of());
        MatchParticipantRepository.WindowedStatRow row = mock(MatchParticipantRepository.WindowedStatRow.class);
        when(row.getUserId()).thenReturn(playerId);
        when(row.getGamesPlayed()).thenReturn(1L);
        when(row.getWins()).thenReturn(1L);
        when(row.getPointsFor()).thenReturn(21L);
        when(row.getPointsAgainst()).thenReturn(12L);
        when(participants.aggregateWindowedStats(groupId, from, to)).thenReturn(List.of(row));

        StatsQueryService service = new StatsQueryService(
                mock(GroupMembershipGuard.class),
                members,
                mock(MemberStatsRepository.class),
                participants,
                mock(MatchService.class),
                mock(AvatarUrlResolver.class),
                mock(MonthlyTrophyService.class),
                mock(MonthlyStandingRepository.class),
                teamRatings,
                true);

        LeaderboardRanker.Standings standings = service.rankedStandings(groupId, from, to, 4);

        assertThat(standings.entries()).singleElement().satisfies(entry -> {
            assertThat(entry.userId()).isEqualTo(playerId);
            assertThat(entry.provisional()).isTrue();
        });
        assertThat(standings.minGamesToRank()).isEqualTo(4);
        verify(teamRatings).replay(eq(groupId), eq(Instant.EPOCH), eq(to), anyMap(), eq(from));
    }
}
