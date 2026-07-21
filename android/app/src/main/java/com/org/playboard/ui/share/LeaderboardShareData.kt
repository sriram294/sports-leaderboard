package com.org.playboard.ui.share

import com.org.playboard.data.model.PlayerRanking

/** How many ranked players the shared leaderboard image includes. */
const val MAX_SHARE_ROWS = 10

/**
 * The top-N rows shown in the shared image, in canonical (server) ranking order.
 *
 * Provisional players are dropped rather than counted against the cap: a share image is a
 * brag artifact, and "3 more games to rank" is a prompt aimed at the player themselves, not
 * something to broadcast. Excluding them also keeps the cap meaning "the top ten players"
 * rather than "ten rows, some of which aren't ranked".
 *
 * Pure so it can be unit-tested without Android.
 */
fun topRankings(all: List<PlayerRanking>): List<PlayerRanking> =
    all.filter { !it.provisional }.take(MAX_SHARE_ROWS)

/**
 * Stable cache file name for the rendered PNG. Kept per-group so a re-share of the
 * same group overwrites its own file rather than piling up temp images.
 */
fun shareImageFileName(groupId: String): String =
    "leaderboard-${groupId.filter { it.isLetterOrDigit() }}.png"
