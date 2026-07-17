package com.org.playboard.data.model

/**
 * One leaderboard row for a group. [rank] is the server-assigned position
 * (win rate desc, then points difference desc, then wins desc) and stays fixed
 * even when the UI re-sorts the table by another column.
 */
data class PlayerRanking(
    val rank: Int,
    val userId: String,
    val displayName: String,
    val photoUrl: String?,
    val avatarId: String?,
    val avatarColor: String,
    val gamesPlayed: Int,
    val wins: Int,
    val losses: Int,
    val pointsFor: Int,
    // Deliberately has no default: the rollout gap is absorbed by the DTO, and a
    // defaulted Int here would silently swallow positionally-passed streak values.
    val pointsAgainst: Int,
    val winRate: Double,
    /** Current run, signed: positive = win streak, negative = loss streak. */
    val currentStreak: Int = 0,
    /** Longest win streak ever (always ≥ 0). */
    val bestStreak: Int = 0,
) {
    /**
     * Win rate as a whole percentage for display (e.g. `0.83` → `83`).
     *
     * Rounded, not truncated: truncating reported 42.86% as "42%", which both
     * understated every rate by up to a point and made distinct rates look tied
     * (two players showing "42%" while the server ranked them apart).
     */
    val winRatePercent: Int get() = Math.round(winRate * 100).toInt()

    /**
     * Points difference (for − against) — the canonical tiebreak once win rates are
     * equal, and the table's points column.
     */
    val pointsDiff: Int get() = pointsFor - pointsAgainst

    /** [pointsDiff] for display; positive values carry an explicit `+` so the sign reads at a glance. */
    val pointsDiffLabel: String get() = if (pointsDiff > 0) "+$pointsDiff" else pointsDiff.toString()
}
