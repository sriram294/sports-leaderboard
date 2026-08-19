package com.org.playboard.service.stats;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.org.playboard.dto.stats.LeaderboardEntryDto;
import com.org.playboard.entity.stats.MonthlyStanding;
import com.org.playboard.repository.stats.MonthlyStandingRepository;
import com.org.playboard.repository.stats.MonthlyTrophyRepository;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MonthlyStandingsWriterTest {
    @Mock private MonthlyTrophyRepository trophyRepository;
    @Mock private MonthlyStandingRepository standingRepository;
    @InjectMocks private MonthlyStandingsWriter writer;

    @Test
    void aClaimStoresEveryEntryIncludingProvisionalPlayers() {
        UUID groupId = UUID.randomUUID();
        YearMonth month = YearMonth.of(2026, 9);
        LeaderboardEntryDto winner = entry(1, false);
        LeaderboardEntryDto provisional = entry(2, true);
        when(trophyRepository.captureIfAbsent(
                eq(groupId), eq(winner.userId()), eq(month.atDay(1)),
                eq(winner.rating()), eq(winner.gamesPlayed()), eq(winner.wins())))
                .thenReturn(1);

        boolean captured = writer.capture(
                groupId,
                month,
                new LeaderboardRanker.Standings(List.of(winner, provisional), 2),
                Optional.of(winner));

        assertThat(captured).isTrue();
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<MonthlyStanding>> rows = ArgumentCaptor.forClass(List.class);
        verify(standingRepository).saveAll(rows.capture());
        assertThat(rows.getValue()).hasSize(2);
        assertThat(rows.getValue()).extracting(MonthlyStanding::isProvisional)
                .containsExactly(false, true);
        verify(standingRepository).flush();
    }

    @Test
    void losingTheMonthClaimWritesNoRows() {
        UUID groupId = UUID.randomUUID();
        YearMonth month = YearMonth.of(2026, 9);
        when(trophyRepository.captureIfAbsent(
                eq(groupId), isNull(), eq(month.atDay(1)), isNull(), isNull(), isNull()))
                .thenReturn(0);

        assertThat(writer.capture(
                groupId, month, new LeaderboardRanker.Standings(List.of(), 1), Optional.empty()))
                .isFalse();

        verify(standingRepository, never()).saveAll(anyList());
        verify(standingRepository, never()).flush();
    }

    private static LeaderboardEntryDto entry(int rank, boolean provisional) {
        return new LeaderboardEntryDto(
                rank, UUID.randomUUID(), "Player", null, null, "#7ED321",
                provisional ? 1 : 5, provisional ? 1 : 3, provisional ? 0 : 2,
                100, 80, new BigDecimal("0.6000"), 0, 0,
                new BigDecimal("42.0"), provisional, List.of());
    }
}
