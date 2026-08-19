package com.org.playboard.service.stats;

import com.org.playboard.dto.stats.LeaderboardEntryDto;
import com.org.playboard.entity.stats.MonthlyStanding;
import com.org.playboard.repository.stats.MonthlyStandingRepository;
import com.org.playboard.repository.stats.MonthlyTrophyRepository;
import com.org.playboard.service.stats.LeaderboardRanker.Standings;
import java.time.YearMonth;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Atomically claims a completed month and freezes its entire eligible leaderboard. */
@Service
public class MonthlyStandingsWriter {
    private final MonthlyTrophyRepository trophyRepository;
    private final MonthlyStandingRepository standingRepository;

    public MonthlyStandingsWriter(
            MonthlyTrophyRepository trophyRepository,
            MonthlyStandingRepository standingRepository) {
        this.trophyRepository = trophyRepository;
        this.standingRepository = standingRepository;
    }

    @Transactional
    public boolean capture(
            UUID groupId,
            YearMonth month,
            Standings standings,
            Optional<LeaderboardEntryDto> winner) {
        LeaderboardEntryDto winningEntry = winner.orElse(null);
        int claimed = trophyRepository.captureIfAbsent(
                groupId,
                winningEntry == null ? null : winningEntry.userId(),
                month.atDay(1),
                winningEntry == null ? null : winningEntry.rating(),
                winningEntry == null ? null : winningEntry.gamesPlayed(),
                winningEntry == null ? null : winningEntry.wins());
        if (claimed == 0) {
            return false;
        }
        standingRepository.saveAll(standings.entries().stream()
                .map(entry -> new MonthlyStanding(groupId, month.atDay(1), entry))
                .toList());
        standingRepository.flush();
        return true;
    }
}
