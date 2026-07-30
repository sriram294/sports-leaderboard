package com.org.playboard.dto.stats;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * One row of the leaderboard.
 *
 * <p>{@code rating} is the Wilson score lower bound on the win rate, scaled to 0-100 with
 * one decimal — a confidence-adjusted win rate, so a small sample scores below a long
 * record at the same raw percentage. {@code provisional} players have played fewer than
 * the group's {@link LeaderboardResponse#minGamesToRank()} and are listed after the ranked
 * ones; they still carry a continuing {@code rank} rather than a sentinel, so older clients
 * that don't know about the flag still render a sanely numbered list.
 *
 * <p>{@code recentForm} is the player's last 10 results within the standings window, in
 * <b>chronological</b> order (oldest first, newest last) — deliberately the opposite of
 * {@link PlayerStatsDto#recentMatches()}, which is newest-first. Chronological order is the
 * render order for a left-to-right form-dots row, so no client needs to reverse it.
 */
public record LeaderboardEntryDto(
        int rank,
        UUID userId,
        String displayName,
        String photoUrl,
        String avatarId,
        String avatarColor,
        int gamesPlayed,
        int wins,
        int losses,
        int pointsFor,
        int pointsAgainst,
        BigDecimal winRate,
        int currentStreak,
        int bestStreak,
        BigDecimal rating,
        boolean provisional,
        List<Boolean> recentForm) {

    /**
     * Copy with a different rank. Lives on the record so the 17-field constructor is
     * splatted in exactly one place — positional construction at several call sites was
     * a standing hazard every time a field was added.
     */
    public LeaderboardEntryDto withRank(int newRank) {
        return new LeaderboardEntryDto(
                newRank, userId, displayName, photoUrl, avatarId, avatarColor,
                gamesPlayed, wins, losses, pointsFor, pointsAgainst,
                winRate, currentStreak, bestStreak, rating, provisional, recentForm);
    }

    /** Copy marked provisional (or not). */
    public LeaderboardEntryDto withProvisional(boolean value) {
        return new LeaderboardEntryDto(
                rank, userId, displayName, photoUrl, avatarId, avatarColor,
                gamesPlayed, wins, losses, pointsFor, pointsAgainst,
                winRate, currentStreak, bestStreak, rating, value, recentForm);
    }

    /** Points difference — the first tiebreak between equal ratings, and shown on the row. */
    public int pointsDiff() {
        return pointsFor - pointsAgainst;
    }
}
