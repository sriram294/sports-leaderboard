package com.org.playboard.service.stats;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.org.playboard.entity.group.GroupMember;
import com.org.playboard.entity.user.User;
import com.org.playboard.entity.stats.MonthlyStanding;
import com.org.playboard.entity.stats.MonthlyTrophy;
import com.org.playboard.repository.group.GroupMemberRepository;
import com.org.playboard.repository.stats.MonthlyStandingRepository;
import com.org.playboard.repository.stats.MonthlyTrophyRepository;
import com.org.playboard.service.group.GroupMembershipGuard;
import com.org.playboard.service.user.AvatarUrlResolver;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ArchivedLeaderboardServiceTest {
    @Test
    void returnsOnlyCapturedMonthsAndUsesFrozenRows() {
        var trophies = mock(MonthlyTrophyRepository.class);
        var standings = mock(MonthlyStandingRepository.class);
        var members = mock(GroupMemberRepository.class);
        var groupId = UUID.randomUUID();
        var callerId = UUID.randomUUID();
        var playerId = UUID.randomUUID();
        var month = LocalDate.of(2026, 9, 1);
        when(trophies.findLatestCapturedMonths(org.mockito.ArgumentMatchers.eq(groupId), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(month));
        var trophy = mock(MonthlyTrophy.class);
        when(trophy.getAlgorithmVersion()).thenReturn("team-v2");
        when(trophy.getMinGamesToRank()).thenReturn(3);
        when(trophies.findByGroupIdAndMonth(groupId, month)).thenReturn(Optional.of(trophy));
        var player = mock(User.class);
        when(player.getId()).thenReturn(playerId);
        when(player.getDisplayName()).thenReturn("Priya");
        when(player.getAvatarColor()).thenReturn("#FF3D8A");
        var member = mock(GroupMember.class);
        when(member.getUser()).thenReturn(player);
        when(members.findByGroupId(groupId)).thenReturn(List.of(member));
        var row = mock(MonthlyStanding.class);
        when(row.getUserId()).thenReturn(playerId);
        when(row.getRank()).thenReturn(1);
        when(row.getGamesPlayed()).thenReturn(6);
        when(row.getWins()).thenReturn(5);
        when(row.getRating()).thenReturn(new BigDecimal("54.1"));
        when(row.isProvisional()).thenReturn(false);
        when(row.getAlgorithmVersion()).thenReturn("team-v2");
        when(standings.findByGroupIdAndMonthOrderByRank(groupId, month)).thenReturn(List.of(row));
        var service = new ArchivedLeaderboardService(trophies, standings, members,
                mock(GroupMembershipGuard.class), mock(AvatarUrlResolver.class));

        assertThat(service.months(groupId, callerId)).containsExactly("2026-09");
        var response = service.standings(groupId, callerId, java.time.YearMonth.of(2026, 9));
        assertThat(response.minGamesToRank()).isEqualTo(3);
        assertThat(response.rankings()).singleElement().satisfies(entry -> {
            assertThat(entry.userId()).isEqualTo(playerId);
            assertThat(entry.rank()).isEqualTo(1);
            assertThat(entry.rating()).isEqualByComparingTo("54.1");
        });
    }
}
