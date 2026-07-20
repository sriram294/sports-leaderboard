package com.org.playboard.service.notification;

import com.org.playboard.dto.stats.LeaderboardEntryDto;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * How one player's leaderboard position moved over a playing session.
 *
 * <p>{@code previousRank} is null when the player wasn't ranked before — their first
 * ever match, or their first since joining. Rank 1 is top, so <em>moving up means the
 * number goes down</em>.
 */
public record RankChange(UUID userId, Integer previousRank, int currentRank) {

    public boolean isFirstTimeRanked() {
        return previousRank == null;
    }

    /** Places gained: positive is a climb, negative a drop. Only meaningful when previously ranked. */
    public int placesGained() {
        return previousRank == null ? 0 : previousRank - currentRank;
    }

    /**
     * Players whose position differs between the two standings. Players who held their
     * rank are omitted — a "you stayed put" push carries no news and is pure volume.
     *
     * <p>Both lists must come from the same ranking routine
     * ({@code StatsQueryService.rankedStandings}); comparing across the all-time and
     * windowed paths can manufacture a change that didn't happen.
     *
     * <p>A player present in {@code before} but absent from {@code after} produces
     * nothing: cumulative standings only grow, so that means they left the group.
     */
    public static List<RankChange> between(List<LeaderboardEntryDto> before, List<LeaderboardEntryDto> after) {
        Map<UUID, Integer> previousRanks = new HashMap<>();
        for (LeaderboardEntryDto entry : before) {
            previousRanks.put(entry.userId(), entry.rank());
        }

        List<RankChange> changes = new ArrayList<>();
        for (LeaderboardEntryDto entry : after) {
            Integer previous = previousRanks.get(entry.userId());
            if (previous == null || previous.intValue() != entry.rank()) {
                changes.add(new RankChange(entry.userId(), previous, entry.rank()));
            }
        }
        return changes;
    }
}
