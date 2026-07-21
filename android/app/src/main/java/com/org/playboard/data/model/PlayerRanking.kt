package com.org.playboard.data.model

import java.util.Locale

/**
 * One leaderboard row for a group. [rank] is the server-assigned position
 * (rating desc, then points difference desc, then wins desc) and stays fixed
 * even when the UI re-sorts the table by another metric.
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
    /**
     * Confidence-adjusted win rate, 0–100. `null` only when talking to a backend that
     * predates ratings — deliberately nullable rather than defaulting to `0.0`, because a
     * winless player legitimately rates 0.0 and the two must stay distinguishable.
     */
    val rating: Double? = null,
    /** Below the group's games threshold: listed, but not ranked. */
    val provisional: Boolean = false,
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

    /**
     * The big right-hand number: the rating to one decimal, `"prov"` while unranked, or
     * the win rate when talking to a pre-rating backend.
     */
    val ratingLabel: String get() = when {
        rating == null -> "$winRatePercent%"
        provisional -> "prov"
        else -> String.format(Locale.US, "%.1f", rating)
    }

    /** Games still needed before this player ranks; 0 once they're over the line. */
    fun gamesNeeded(minGamesToRank: Int): Int = (minGamesToRank - gamesPlayed).coerceAtLeast(0)

    /**
     * The row's second line, e.g. `"37 games · 22-15 · 59% · +76"`.
     *
     * The trailing points difference is not decoration: it is the first tiebreak between
     * equal ratings, so without it two players on the same rating would have no visible
     * reason for their order. Provisional players trade it for what they actually need to
     * know — how many more games until they rank.
     */
    fun secondaryLine(minGamesToRank: Int): String {
        val head = "$gamesPlayed games · $wins-$losses · $winRatePercent%"
        val needed = gamesNeeded(minGamesToRank)
        return if (provisional && needed > 0) "$head · $needed more to rank" else "$head · $pointsDiffLabel"
    }
}
