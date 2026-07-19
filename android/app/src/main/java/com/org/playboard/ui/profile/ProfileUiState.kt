package com.org.playboard.ui.profile

import com.org.playboard.data.model.Match
import com.org.playboard.data.model.MatchSet
import com.org.playboard.data.model.PlayerStats
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

/** Immutable state for the Profile tab (docs/requirements/05-profile.md). */
data class ProfileUiState(
    val isLoading: Boolean = true,
    val hasLoadFailed: Boolean = false,
    val noGroup: Boolean = false,
    val groupName: String? = null,
    /** Signed-in user's email — shown in the account row (own profile only). */
    val email: String? = null,
    /**
     * The signed-in user's own display name / avatar, sourced from the live
     * session rather than the stats payload so an in-app rename or photo upload
     * shows on the identity card immediately (req #3). Ignored when viewing
     * another player, whose identity comes from [stats].
     */
    val ownDisplayName: String? = null,
    val ownPhotoUrl: String? = null,
    val ownAvatarId: String? = null,
    /**
     * Whether this is the signed-in user's own profile. Own profile shows the
     * account section (email + Sign out) and the edit affordances; a viewed
     * player's does not (req #2).
     */
    val isOwnProfile: Boolean = true,
    val stats: PlayerStats? = null,
    /**
     * The calendar months shown by the attendance heatmap (the last 3, oldest first), and
     * the local days within them the player was in a match. Attendance loads independently
     * of stats and degrades silently, so these stay defaulted on a failure.
     */
    val attendanceMonths: List<YearMonth> = emptyList(),
    val attendanceDays: Set<LocalDate> = emptySet(),
    /** An avatar upload is in flight — the identity card shows a spinner. */
    val isUploadingPhoto: Boolean = false,
    /** A retryable rename/upload failure to surface, cleared on the next attempt. */
    val updateError: String? = null,
    /** The "edit name" sheet; `null` when closed. */
    val renameSheet: EditNameSheetState? = null,
) {
    /** Recent matches rendered relative to the viewed player (partner vs opponents). */
    val recentMatches: List<RecentMatchRow>
        get() = stats?.let { s -> s.recentMatches.map { it.toRow(s.userId) } } ?: emptyList()

    /** Name shown on the identity card — the live session name for own profile. */
    val displayName: String?
        get() = if (isOwnProfile) ownDisplayName ?: stats?.displayName else stats?.displayName

    /** Avatar photo shown on the identity card — the live session photo for own profile. */
    val identityPhotoUrl: String?
        get() = if (isOwnProfile) ownPhotoUrl ?: stats?.photoUrl else stats?.photoUrl

    /**
     * Default-avatar id shown on the identity card. For own profile it comes from
     * the live session so a just-picked avatar shows immediately; a viewed player's
     * comes from [stats]. Only rendered when there's no photo (photo takes priority).
     */
    val identityAvatarId: String?
        get() = if (isOwnProfile) ownAvatarId ?: stats?.avatarId else stats?.avatarId
}

/**
 * State of the "edit name" bottom sheet (own profile). `null` on
 * [ProfileUiState.renameSheet] means it's closed; [input] is the edited name
 * seeded with the current one; [hasFailed] marks a retryable error.
 */
data class EditNameSheetState(
    val input: String,
    val isSubmitting: Boolean = false,
    val hasFailed: Boolean = false,
) {
    /** Submit is allowed only with non-blank input and no in-flight request. */
    val canSubmit: Boolean get() = input.isNotBlank() && !isSubmitting
}

/**
 * One "Recent Matches" entry, framed from the viewed player's perspective:
 * who they played *with* vs *against*, and whether they won.
 */
data class RecentMatchRow(
    val matchId: String,
    val playedAt: Instant,
    val isWin: Boolean,
    val partnerNames: String,
    val opponentNames: String,
    val sets: List<MatchSet>,
)

private fun Match.toRow(userId: String): RecentMatchRow {
    val myTeam = teams.firstOrNull { team -> team.players.any { it.userId == userId } }
    val opponents = teams.firstOrNull { it.teamNo != myTeam?.teamNo }
    val partners = myTeam?.players.orEmpty().filter { it.userId != userId }
    return RecentMatchRow(
        matchId = id,
        playedAt = playedAt,
        isWin = isWinFor(userId) == true,
        partnerNames = partners.joinToString(" & ") { it.displayName },
        opponentNames = opponents?.players.orEmpty().joinToString(" & ") { it.displayName },
        sets = sets,
    )
}
