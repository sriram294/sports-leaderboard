package com.org.playboard.service.stats;

import static org.assertj.core.api.Assertions.assertThat;

import com.org.playboard.dto.stats.LeaderboardEntryDto;
import com.org.playboard.service.stats.LeaderboardRanker.RawStatRow;
import com.org.playboard.service.stats.LeaderboardRanker.Standings;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Pure ranking tests — no Spring, no DB. Nearly all the value of this feature is here:
 * the maths is invisible in the UI, so if it silently drifts nothing else catches it.
 */
class LeaderboardRankerTest {

    // Deterministic ids so the trailing user-id tiebreak is predictable.
    private static UUID id(int n) {
        return UUID.fromString("00000000-0000-0000-0000-%012d".formatted(n));
    }

    private static RawStatRow row(int idN, int games, int wins) {
        return new RawStatRow(id(idN), games, wins, 0, 0, 0, 0);
    }

    private static RawStatRow row(int idN, int games, int wins, int pf, int pa) {
        return new RawStatRow(id(idN), games, wins, pf, pa, 0, 0);
    }

    /** Minimal factory — the real one resolves a User; ranking doesn't care. */
    private static Standings rank(List<RawStatRow> rows, Integer override) {
        return LeaderboardRanker.rank(rows, override, (r, rating, provisional) -> new LeaderboardEntryDto(
                0, r.userId(), r.userId().toString(), null, null, "#000000",
                r.gamesPlayed(), r.wins(), r.gamesPlayed() - r.wins(),
                r.pointsFor(), r.pointsAgainst(),
                LeaderboardRanker.winRate(r.wins(), r.gamesPlayed()),
                r.currentStreak(), r.bestStreak(), rating, provisional));
    }

    @Nested
    @DisplayName("the case this feature exists for")
    class MotivatingCase {

        @Test
        void the_rating_alone_does_not_demote_a_hot_newcomer() {
            // Worth stating plainly, because it is counter-intuitive and constrains the
            // design: 6-1 is genuinely strong evidence, so Wilson rates mugu (~48.7) ABOVE
            // Sriram's 22-15 (~43.5). No honest confidence bound reverses that — 59% over
            // 37 games is a well-established mediocre record, not a better one.
            //
            // Which is precisely why the sliding threshold exists. The rating fixes
            // small-sample *inflation*; the threshold defers judgement until there is
            // enough evidence to compare fairly. See the partition test below.
            assertThat(LeaderboardRanker.wilsonLowerBound(6, 7))
                    .isGreaterThan(LeaderboardRanker.wilsonLowerBound(22, 37));
        }

        @Test
        void the_threshold_is_what_keeps_a_hot_newcomer_off_the_board() {
            // The end-to-end guarantee the feature actually promises.
            Standings standings = rank(List.of(
                    row(1, 7, 6),      // mugu   — 86%, best raw rate
                    row(2, 37, 22),    // Sriram — 59%
                    row(3, 50, 27)),   // Dinesh — 54%
                    null);

            LeaderboardEntryDto mugu = standings.entries().stream()
                    .filter(e -> e.userId().equals(id(1))).findFirst().orElseThrow();

            assertThat(mugu.provisional()).isTrue();
            assertThat(standings.entries().get(standings.entries().size() - 1).userId())
                    .as("provisional players sort below every ranked one")
                    .isEqualTo(id(1));
        }

        @Test
        void rating_rises_with_games_at_a_constant_win_rate() {
            // The property that makes playing more worthwhile: same 80%, more evidence.
            double g10 = LeaderboardRanker.wilsonLowerBound(8, 10);
            double g15 = LeaderboardRanker.wilsonLowerBound(12, 15);
            double g20 = LeaderboardRanker.wilsonLowerBound(16, 20);
            double g30 = LeaderboardRanker.wilsonLowerBound(24, 30);

            assertThat(g10).isLessThan(g15);
            assertThat(g15).isLessThan(g20);
            assertThat(g20).isLessThan(g30);
        }

        @Test
        void a_grinder_with_a_poor_rate_stays_near_the_bottom() {
            // The failure mode of every volume-weighted alternative: 42-78 is 35%.
            double grinder = LeaderboardRanker.wilsonLowerBound(42, 120);
            double modest = LeaderboardRanker.wilsonLowerBound(11, 21); // 52% over 21

            assertThat(grinder).isLessThan(modest);
        }
    }

    @Nested
    @DisplayName("wilsonLowerBound")
    class Wilson {

        @Test
        void zero_games_is_zero_and_never_nan() {
            double result = LeaderboardRanker.wilsonLowerBound(0, 0);
            assertThat(Double.isNaN(result)).as("NaN would corrupt the comparator silently").isFalse();
            assertThat(result).isZero();
        }

        @Test
        void a_winless_record_is_zero() {
            assertThat(LeaderboardRanker.wilsonLowerBound(0, 10)).isZero();
        }

        @Test
        void a_perfect_record_is_discounted_by_sample_size() {
            // 100% win rate, but the bound is far below 100 and climbs with games.
            double perfect3 = LeaderboardRanker.wilsonLowerBound(3, 3);
            double perfect10 = LeaderboardRanker.wilsonLowerBound(10, 10);

            assertThat(perfect3).isBetween(40d, 50d);
            assertThat(perfect10).isBetween(70d, 75d);
            assertThat(perfect3).isLessThan(perfect10);
        }

        @Test
        void a_single_win_scores_below_a_long_solid_record() {
            // Guards the "flash in the pan" case the threshold is a second line of defence for.
            assertThat(LeaderboardRanker.wilsonLowerBound(1, 1))
                    .isLessThan(LeaderboardRanker.wilsonLowerBound(22, 37));
        }
    }

    @Nested
    @DisplayName("minGamesToRank")
    class Threshold {

        @Test
        void is_half_the_median_rounded_up() {
            // median of [4, 8, 12] is 8 -> 4
            assertThat(LeaderboardRanker.minGamesToRank(List.of(4, 8, 12))).isEqualTo(4);
        }

        @Test
        void an_even_sized_group_averages_the_two_middle_values() {
            // median of [3, 4] is 3.5 -> ceil(1.75) = 2
            assertThat(LeaderboardRanker.minGamesToRank(List.of(3, 4))).isEqualTo(2);
        }

        @Test
        void is_capped_so_it_never_exceeds_ten() {
            assertThat(LeaderboardRanker.minGamesToRank(List.of(40, 50, 60))).isEqualTo(10);
        }

        @Test
        void is_floored_at_one_so_a_zero_never_ranks_everyone() {
            assertThat(LeaderboardRanker.minGamesToRank(List.of(1, 1, 1))).isEqualTo(1);
        }

        @Test
        void ignores_players_who_have_not_played() {
            // Zeroes would drag the median down and make the threshold meaninglessly low.
            assertThat(LeaderboardRanker.minGamesToRank(List.of(0, 0, 8, 12)))
                    .isEqualTo(LeaderboardRanker.minGamesToRank(List.of(8, 12)));
        }

        @Test
        void an_empty_group_falls_back_to_one() {
            assertThat(LeaderboardRanker.minGamesToRank(List.of())).isEqualTo(1);
            assertThat(LeaderboardRanker.minGamesToRank(List.of(0, 0))).isEqualTo(1);
        }

        @Test
        void early_in_a_month_everyone_still_ranks() {
            // The reason the threshold slides. After one session nobody has an evidence
            // advantage, so a fixed gate of 10 would leave the board empty.
            List<RawStatRow> rows = List.of(row(1, 4, 3), row(2, 4, 2), row(3, 4, 1), row(4, 2, 2));
            Standings standings = rank(rows, null);

            assertThat(standings.minGamesToRank()).isLessThanOrEqualTo(4);
            assertThat(standings.entries()).allMatch(e -> !e.provisional());
        }
    }

    @Nested
    @DisplayName("ordering and partitioning")
    class Ordering {

        @Test
        void provisional_players_come_after_every_ranked_player() {
            // mugu (7 games, 86%) has the best raw rate but the fewest games.
            List<RawStatRow> rows = List.of(
                    row(1, 7, 6),    // provisional under a threshold of 10
                    row(2, 37, 22),
                    row(3, 50, 27));
            Standings standings = rank(rows, 10);

            assertThat(standings.entries()).hasSize(3);
            assertThat(standings.entries().get(0).provisional()).isFalse();
            assertThat(standings.entries().get(1).provisional()).isFalse();
            assertThat(standings.entries().get(2).provisional()).isTrue();
            assertThat(standings.entries().get(2).userId()).isEqualTo(id(1));
        }

        @Test
        void ranks_are_dense_and_continue_through_the_provisional_block() {
            // Continuing ranks (not a 0 sentinel) so older clients that ignore the flag
            // still render a sanely numbered list.
            Standings standings = rank(List.of(row(1, 7, 6), row(2, 37, 22), row(3, 50, 27)), 10);

            assertThat(standings.entries()).extracting(LeaderboardEntryDto::rank)
                    .containsExactly(1, 2, 3);
        }

        @Test
        void players_with_no_games_are_omitted_entirely() {
            Standings standings = rank(List.of(row(1, 0, 0), row(2, 12, 7)), null);

            assertThat(standings.entries()).hasSize(1);
            assertThat(standings.entries().get(0).userId()).isEqualTo(id(2));
        }

        @Test
        void equal_ratings_break_on_points_difference() {
            // Identical records, so identical ratings — diff decides, and it's the value
            // rendered on the row so the order has a visible reason.
            Standings standings = rank(List.of(
                    row(1, 20, 12, 400, 380),   // +20
                    row(2, 20, 12, 400, 300)),  // +100
                    null);

            assertThat(standings.entries().get(0).userId()).isEqualTo(id(2));
            assertThat(standings.entries().get(0).pointsDiff()).isEqualTo(100);
            assertThat(standings.entries().get(1).pointsDiff()).isEqualTo(20);
        }

        @Test
        void ordering_uses_the_unrounded_bound() {
            // Both round to the same displayed rating; the finer value must still decide,
            // or the order shuffles between requests.
            Standings standings = rank(List.of(row(1, 37, 22), row(2, 41, 24)), null);

            BigDecimal first = standings.entries().get(0).rating();
            BigDecimal second = standings.entries().get(1).rating();
            assertThat(first).isGreaterThanOrEqualTo(second);
            assertThat(LeaderboardRanker.wilsonLowerBound(22, 37))
                    .isGreaterThan(LeaderboardRanker.wilsonLowerBound(24, 41));
            assertThat(standings.entries().get(0).userId()).isEqualTo(id(1));
        }

        @Test
        void fully_tied_rows_stay_in_a_stable_order() {
            Standings a = rank(List.of(row(2, 10, 5, 200, 200), row(1, 10, 5, 200, 200)), null);
            Standings b = rank(List.of(row(1, 10, 5, 200, 200), row(2, 10, 5, 200, 200)), null);

            assertThat(a.entries().stream().map(LeaderboardEntryDto::userId))
                    .containsExactlyElementsOf(b.entries().stream().map(LeaderboardEntryDto::userId).toList());
        }

        @Test
        void a_pinned_threshold_overrides_the_derived_one() {
            // The mechanism that stops median drift firing bogus rank-change pushes.
            List<RawStatRow> rows = List.of(row(1, 7, 6), row(2, 37, 22));

            assertThat(rank(rows, 10).entries())
                    .filteredOn(LeaderboardEntryDto::provisional).hasSize(1);
            assertThat(rank(rows, 1).entries())
                    .filteredOn(LeaderboardEntryDto::provisional).isEmpty();
        }
    }

    @Nested
    @DisplayName("the live Old Monk board")
    class AcceptanceCase {

        @Test
        void reproduces_the_expected_ordering() {
            // Real This Month data. Threshold lands on 10 (median 29). The two moves that
            // matter: mugu (86%, 7 games) becomes provisional, and Yuvaraj (60%, 10 games)
            // falls from 2nd to 6th — the threshold alone would not have caught Yuvaraj.
            List<RawStatRow> rows = List.of(
                    new RawStatRow(id(1), 7, 6, 0, 0, 0, 0),      // mugu
                    new RawStatRow(id(2), 10, 6, 0, 0, 0, 0),     // Yuvaraj
                    new RawStatRow(id(3), 37, 22, 0, 0, 0, 0),    // Sriram
                    new RawStatRow(id(4), 41, 24, 0, 0, 0, 0),    // Mani
                    new RawStatRow(id(5), 50, 27, 0, 0, 0, 0),    // Dinesh K
                    new RawStatRow(id(6), 21, 11, 0, 0, 0, 0),    // Raja
                    new RawStatRow(id(7), 36, 18, 0, 0, 0, 0),    // Balaji
                    new RawStatRow(id(8), 30, 13, 0, 0, 0, 0),    // deenesh
                    new RawStatRow(id(9), 28, 11, 0, 0, 0, 0),    // Pori
                    new RawStatRow(id(10), 26, 9, 0, 0, 0, 0));   // Barath

            Standings standings = rank(rows, null);

            assertThat(standings.minGamesToRank()).isEqualTo(10);
            assertThat(standings.entries()).extracting(LeaderboardEntryDto::userId)
                    .containsExactly(
                            id(3), id(4), id(5), id(7), id(6), id(2), id(8), id(9), id(10),
                            id(1)); // mugu last, provisional
            assertThat(standings.entries().get(9).provisional()).isTrue();
            assertThat(standings.entries()).filteredOn(LeaderboardEntryDto::provisional).hasSize(1);
        }
    }
}
