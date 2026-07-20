package com.org.playboard.service.notification;

import static com.org.playboard.service.notification.SessionRankChangeJob.describe;
import static com.org.playboard.service.notification.SessionRankChangeJob.endedSessionStart;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SessionRankChangeJobTest {

    private static final Duration QUIET = Duration.ofMinutes(90);
    private static final Instant EVENING = Instant.parse("2026-07-20T19:00:00Z");

    private static Instant at(int minutesAfterEvening) {
        return EVENING.plus(Duration.ofMinutes(minutesAfterEvening));
    }

    @Test
    void noPlayTimesMeansNoSession() {
        assertTrue(endedSessionStart(List.of(), at(300), QUIET).isEmpty());
    }

    @Test
    void aGroupStillPlayingHasNoEndedSession() {
        // Last match 30 minutes ago — well inside the quiet period.
        List<Instant> played = List.of(at(0), at(20), at(50));

        assertTrue(endedSessionStart(played, at(80), QUIET).isEmpty());
    }

    @Test
    void sessionStartsAtTheFirstMatchOfTheContiguousBlock() {
        List<Instant> played = List.of(at(0), at(20), at(50));

        assertEquals(Optional.of(at(0)), endedSessionStart(played, at(200), QUIET));
    }

    @Test
    void anEarlierSessionSeparatedByALongGapIsExcluded() {
        // Two blocks: a lunchtime pair, then an evening block 4 hours later. Only the
        // evening block is "this session" — the baseline must not reach back past the gap.
        List<Instant> played = List.of(at(0), at(20), at(260), at(280), at(300));

        assertEquals(Optional.of(at(260)), endedSessionStart(played, at(450), QUIET));
    }

    @Test
    void aGapExactlyTheQuietPeriodSplitsTheSession() {
        List<Instant> played = List.of(at(0), at(90));

        // The boundary is inclusive: a gap of exactly the quiet period ends the session,
        // matching the rule that "no match for >= quietPeriod" means finished.
        assertEquals(Optional.of(at(90)), endedSessionStart(played, at(300), QUIET));
    }

    @Test
    void aSingleMatchIsItsOwnSession() {
        assertEquals(Optional.of(at(0)), endedSessionStart(List.of(at(0)), at(200), QUIET));
    }

    @Test
    void copyReadsNaturallyForEachOutcome() {
        UUID user = UUID.randomUUID();

        assertEquals(
                "You moved up 2 places — you're #3 in Smashers.",
                describe(new RankChange(user, 5, 3), "Smashers"));
        assertEquals(
                "You moved up 1 place — you're #1 in Smashers.",
                describe(new RankChange(user, 2, 1), "Smashers"));
        assertEquals("You slipped 1 place to #4 in Smashers.", describe(new RankChange(user, 3, 4), "Smashers"));
        assertEquals("You slipped 3 places to #6 in Smashers.", describe(new RankChange(user, 3, 6), "Smashers"));
        assertEquals(
                "You're on the board at #7 in Smashers.", describe(new RankChange(user, null, 7), "Smashers"));
    }
}
