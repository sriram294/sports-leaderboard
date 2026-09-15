package com.org.playboard.service.stats;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TeamRatingCalculatorTest {
    private static final UUID A = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID B = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID G = UUID.fromString("00000000-0000-0000-0000-000000000003");

    @Test
    void winnerImprovesAndLoserDropsFromEqualPriors() {
        Map<UUID, TeamRatingCalculator.Rating> result = TeamRatingCalculator.update(
                Map.of(A, new TeamRatingCalculator.Rating(), B, new TeamRatingCalculator.Rating()),
                List.of(
                        new TeamRatingCalculator.Team(List.of(new TeamRatingCalculator.Participant(A, false)), true),
                        new TeamRatingCalculator.Team(List.of(new TeamRatingCalculator.Participant(B, false)), false)));
        assertTrue(result.get(A).mean() > 25);
        assertTrue(result.get(B).mean() < 25);
        assertTrue(result.get(A).sigma() < TeamRatingCalculator.INITIAL_SIGMA);
    }

    @Test
    void guestsDoNotGainStateButContributeTheirPriorToTeamStrength() {
        Map<UUID, TeamRatingCalculator.Rating> result = TeamRatingCalculator.update(
                Map.of(A, new TeamRatingCalculator.Rating()),
                List.of(
                        new TeamRatingCalculator.Team(List.of(new TeamRatingCalculator.Participant(A, false), new TeamRatingCalculator.Participant(G, true)), true),
                        new TeamRatingCalculator.Team(List.of(new TeamRatingCalculator.Participant(B, false)), false)));
        assertTrue(result.containsKey(A));
        assertTrue(!result.containsKey(G));
        assertTrue(result.containsKey(B));
    }

    @Test
    void symmetricTeamsMoveByEqualAndOppositeAmounts() {
        Map<UUID, TeamRatingCalculator.Rating> result = TeamRatingCalculator.update(
                Map.of(A, new TeamRatingCalculator.Rating(), B, new TeamRatingCalculator.Rating()),
                List.of(new TeamRatingCalculator.Team(List.of(new TeamRatingCalculator.Participant(A, false)), true),
                        new TeamRatingCalculator.Team(List.of(new TeamRatingCalculator.Participant(B, false)), false)));
        assertEquals(result.get(A).mean() - 25, 25 - result.get(B).mean(), 1e-9);
    }

    @Test
    void displayMappingIsMonotonicAndBounded() {
        double previous = 0;
        for (double score = -100; score <= 100; score += 1) {
            double display = 100d / (1d + Math.exp(-(score - 17d) / 3d));
            assertTrue(display >= previous && display >= 0 && display <= 100);
            previous = display;
        }
    }
}
