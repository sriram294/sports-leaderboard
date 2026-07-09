package com.org.playboard.service.stats;

import com.org.playboard.entity.group.Group;
import com.org.playboard.entity.stats.MemberStats;
import com.org.playboard.repository.match.MatchParticipantRepository;
import com.org.playboard.repository.match.MatchParticipantRepository.PlayerMatchRow;
import com.org.playboard.repository.match.MatchSetRepository;
import com.org.playboard.repository.match.MatchSetRepository.MatchScoreTotals;
import com.org.playboard.repository.stats.MemberStatsRepository;
import com.org.playboard.repository.user.UserRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Recomputes {@code member_stats} for a set of players by fully rescanning
 * each player's non-deleted matches in the group, rather than incrementally
 * patching sums as data-model.md's original sketch proposed. Both approaches
 * are "order-independent for sums, scan-required for streaks" per that doc —
 * a full rescan just does both in the same pass, with the same cost bound
 * (one player's match count in one group) the doc already accepts for
 * streaks alone, and it can't drift out of sync the way hand-rolled
 * increment/decrement-on-edit math could. Always called inside the same
 * {@code @Transactional} as the match write that triggered it.
 */
@Component
public class StatsRecalculationService {

    private final MatchParticipantRepository matchParticipantRepository;
    private final MatchSetRepository matchSetRepository;
    private final MemberStatsRepository memberStatsRepository;
    private final UserRepository userRepository;

    public StatsRecalculationService(
            MatchParticipantRepository matchParticipantRepository,
            MatchSetRepository matchSetRepository,
            MemberStatsRepository memberStatsRepository,
            UserRepository userRepository) {
        this.matchParticipantRepository = matchParticipantRepository;
        this.matchSetRepository = matchSetRepository;
        this.memberStatsRepository = memberStatsRepository;
        this.userRepository = userRepository;
    }

    public void recompute(Group group, Set<UUID> userIds) {
        for (UUID userId : userIds) {
            recomputeForPlayer(group, userId);
        }
    }

    private void recomputeForPlayer(Group group, UUID userId) {
        List<PlayerMatchRow> history = matchParticipantRepository.findPlayerMatchHistory(group.getId(), userId);
        MemberStats stats = memberStatsRepository
                .findByGroupIdAndUserId(group.getId(), userId)
                .orElseGet(() -> new MemberStats(group, userRepository.getReferenceById(userId)));

        if (history.isEmpty()) {
            stats.setMatchesPlayed(0);
            stats.setWins(0);
            stats.setLosses(0);
            stats.setPointsFor(0);
            stats.setPointsAgainst(0);
            stats.setCurrentStreak(0);
            stats.setBestStreak(0);
            memberStatsRepository.save(stats);
            return;
        }

        Map<UUID, MatchScoreTotals> totalsByMatch =
                matchSetRepository.sumScoresByMatchIds(history.stream().map(PlayerMatchRow::getMatchId).toList())
                        .stream()
                        .collect(Collectors.toMap(MatchScoreTotals::getMatchId, t -> t));

        int wins = 0;
        int pointsFor = 0;
        int pointsAgainst = 0;
        List<Boolean> resultsInOrder = new ArrayList<>(history.size());

        for (PlayerMatchRow row : history) {
            resultsInOrder.add(row.isWinner());
            if (row.isWinner()) {
                wins++;
            }
            MatchScoreTotals totals = totalsByMatch.get(row.getMatchId());
            if (totals != null) {
                boolean onTeam1 = row.getTeamNo() == 1;
                pointsFor += onTeam1 ? totals.getTeam1Total() : totals.getTeam2Total();
                pointsAgainst += onTeam1 ? totals.getTeam2Total() : totals.getTeam1Total();
            }
        }

        int[] streaks = computeStreaks(resultsInOrder);

        stats.setMatchesPlayed(history.size());
        stats.setWins(wins);
        stats.setLosses(history.size() - wins);
        stats.setPointsFor(pointsFor);
        stats.setPointsAgainst(pointsAgainst);
        stats.setCurrentStreak(streaks[0]);
        stats.setBestStreak(streaks[1]);
        memberStatsRepository.save(stats);
    }

    // results are in chronological order (oldest first). currentStreak is the
    // trailing run signed by its kind (positive = win streak, negative = loss
    // streak, per data-model.md); bestStreak is the longest win run ever seen.
    private int[] computeStreaks(List<Boolean> resultsInOrder) {
        int runningWinStreak = 0;
        int best = 0;
        for (boolean win : resultsInOrder) {
            if (win) {
                runningWinStreak++;
                best = Math.max(best, runningWinStreak);
            } else {
                runningWinStreak = 0;
            }
        }

        boolean lastWin = resultsInOrder.get(resultsInOrder.size() - 1);
        int trailing = 0;
        for (int i = resultsInOrder.size() - 1; i >= 0 && resultsInOrder.get(i) == lastWin; i--) {
            trailing++;
        }
        int current = lastWin ? trailing : -trailing;

        return new int[] {current, best};
    }
}
