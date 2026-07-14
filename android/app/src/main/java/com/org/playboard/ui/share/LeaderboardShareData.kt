package com.org.playboard.ui.share

import com.org.playboard.data.model.PlayerRanking

/** How many ranked players the shared leaderboard image includes. */
const val MAX_SHARE_ROWS = 10

/**
 * The top-N rows shown in the shared image, in canonical (server) ranking order.
 * Pure so it can be unit-tested without Android.
 */
fun topRankings(all: List<PlayerRanking>): List<PlayerRanking> = all.take(MAX_SHARE_ROWS)

/**
 * Stable cache file name for the rendered PNG. Kept per-group so a re-share of the
 * same group overwrites its own file rather than piling up temp images.
 */
fun shareImageFileName(groupId: String): String =
    "leaderboard-${groupId.filter { it.isLetterOrDigit() }}.png"
