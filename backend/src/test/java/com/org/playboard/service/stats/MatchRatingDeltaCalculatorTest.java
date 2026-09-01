package com.org.playboard.service.stats;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Pure delta-walk tests — no Spring, no DB. Values below were computed independently with
 * the Wilson formula (not re-derived from {@link LeaderboardRanker} at test time), so these
 * also guard {@link LeaderboardRanker#rating} against silent drift, same rationale as
 * {@link LeaderboardRankerTest}.
 */
class MatchRatingDeltaCalculatorTest {

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }

    @Nested
    @DisplayName("a player's first-ever match")
    class FirstMatch {

        @Test
        void a_win_gains_the_full_rating_from_zero() {
            // rating(0,0) = 0.0, rating(1,1) = 20.7
            BigDecimal delta = MatchRatingDeltaCalculator.delta(List.of(true), 0);
            assertThat(delta).isEqualByComparingTo(bd("20.7"));
        }

        @Test
        void a_loss_stays_at_zero() {
            // A winless record is zero regardless of games played, so there is nothing to lose.
            BigDecimal delta = MatchRatingDeltaCalculator.delta(List.of(false), 0);
            assertThat(delta).isEqualByComparingTo(BigDecimal.ZERO.setScale(1));
        }
    }

    @Nested
    @DisplayName("a match mid-history")
    class MidHistory {

        @Test
        void uses_the_record_immediately_before_this_match_not_from_zero() {
            // History: win, loss, WIN(target), loss. Before the target: 1 win of 2 games
            // (rating 9.5). After: 2 wins of 3 (rating 20.8). Proves the walk starts from
            // the player's state just before this match, not from game 0 every time.
            List<Boolean> history = List.of(true, false, true, false);
            BigDecimal delta = MatchRatingDeltaCalculator.delta(history, 2);
            assertThat(delta).isEqualByComparingTo(bd("11.3"));
        }

        @Test
        void a_loss_after_a_long_streak_lowers_the_rating() {
            // 10 straight wins (rating 72.2), then a loss (10-11, rating 62.3). The "more
            // games raises rating at a constant rate" property only holds at a CONSTANT
            // rate — a loss that drops the win rate can and does lower it.
            List<Boolean> history = IntStream.range(0, 10).mapToObj(i -> true)
                    .collect(Collectors.toCollection(java.util.ArrayList::new));
            history.add(false);
            BigDecimal delta = MatchRatingDeltaCalculator.delta(history, 10);
            assertThat(delta).isEqualByComparingTo(bd("-9.9"));
        }
    }

    @Nested
    @DisplayName("sign convention")
    class SignConvention {

        @Test
        void a_win_is_never_negative() {
            BigDecimal delta = MatchRatingDeltaCalculator.delta(List.of(false, false, true), 2);
            assertThat(delta).isGreaterThanOrEqualTo(BigDecimal.ZERO);
        }

        @Test
        void a_loss_can_go_negative_once_games_have_accumulated() {
            BigDecimal delta = MatchRatingDeltaCalculator.delta(List.of(true, true, true, false), 3);
            assertThat(delta).isLessThan(BigDecimal.ZERO);
        }
    }
}
