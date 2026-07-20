package com.org.playboard.service.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.org.playboard.dto.stats.LeaderboardEntryDto;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RankChangeTest {

    private static final UUID ALICE = UUID.randomUUID();
    private static final UUID BOB = UUID.randomUUID();
    private static final UUID CARA = UUID.randomUUID();

    private static LeaderboardEntryDto entry(UUID userId, int rank) {
        return new LeaderboardEntryDto(
                rank, userId, "Player", null, null, null, 10, 5, 5, 100, 90, BigDecimal.valueOf(0.5), 0, 0);
    }

    @Test
    void reportsAClimbAsPositivePlacesGained() {
        List<RankChange> changes =
                RankChange.between(List.of(entry(ALICE, 3)), List.of(entry(ALICE, 1)));

        assertEquals(1, changes.size());
        RankChange change = changes.get(0);
        assertEquals(2, change.placesGained());
        assertEquals(1, change.currentRank());
    }

    @Test
    void reportsADropAsNegativePlacesGained() {
        List<RankChange> changes =
                RankChange.between(List.of(entry(ALICE, 2)), List.of(entry(ALICE, 5)));

        assertEquals(-3, changes.get(0).placesGained());
    }

    @Test
    void playersWhoHeldTheirPositionAreOmitted() {
        List<RankChange> changes =
                RankChange.between(List.of(entry(ALICE, 2)), List.of(entry(ALICE, 2)));

        assertTrue(changes.isEmpty());
    }

    @Test
    void aPlayerRankedForTheFirstTimeHasNoPreviousRank() {
        List<RankChange> changes = RankChange.between(List.of(), List.of(entry(ALICE, 4)));

        assertEquals(1, changes.size());
        assertTrue(changes.get(0).isFirstTimeRanked());
        assertNull(changes.get(0).previousRank());
    }

    @Test
    void picksUpAPlayerPushedDownWithoutPlaying() {
        // Cara sat out; Bob's results overtook her, so her rank moved even though she
        // logged nothing. She is exactly the person who should hear about it.
        List<RankChange> changes = RankChange.between(
                List.of(entry(ALICE, 1), entry(CARA, 2), entry(BOB, 3)),
                List.of(entry(ALICE, 1), entry(BOB, 2), entry(CARA, 3)));

        assertEquals(2, changes.size());
        assertEquals(BOB, changes.get(0).userId());
        assertEquals(1, changes.get(0).placesGained());
        assertEquals(CARA, changes.get(1).userId());
        assertEquals(-1, changes.get(1).placesGained());
    }

    @Test
    void aPlayerWhoLeftTheGroupProducesNothing() {
        List<RankChange> changes =
                RankChange.between(List.of(entry(ALICE, 1), entry(BOB, 2)), List.of(entry(ALICE, 1)));

        assertTrue(changes.isEmpty());
    }
}
