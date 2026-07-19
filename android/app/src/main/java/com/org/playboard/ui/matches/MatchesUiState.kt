package com.org.playboard.ui.matches

import com.org.playboard.data.model.Match
import com.org.playboard.data.model.MatchDetail
import java.time.LocalDate
import java.time.ZoneId

/** Matches grouped under one calendar day (docs/requirements/03-matches.md). */
data class MatchDateSection(val date: LocalDate, val matches: List<Match>)

/** Immutable state for the Matches tab. */
data class MatchesUiState(
    val isLoading: Boolean = true,
    val hasLoadFailed: Boolean = false,
    /** A user-initiated pull-to-refresh is in flight (drives the pull indicator). */
    val isRefreshing: Boolean = false,
    val noGroup: Boolean = false,
    val groupId: String? = null,
    val groupName: String? = null,
    val currentUserId: String? = null,
    /** Viewer is owner/admin — may delete any match, not just their own. */
    val canModerate: Boolean = false,
    val matches: List<Match> = emptyList(),
    /** Cursor for the next older page; `null` once the log is fully loaded. */
    val nextCursor: String? = null,
    /** An older page is being fetched (drives the "Load older matches" footer). */
    val isLoadingMore: Boolean = false,
    /** Per-day expand/collapse overrides; absent days fall back to the default. */
    val expandedDates: Map<LocalDate, Boolean> = emptyMap(),
    val expandedId: String? = null,
    val detail: MatchDetail? = null,
    val isDetailLoading: Boolean = false,
    val detailFailed: Boolean = false,
    val deleteTargetId: String? = null,
    val isDeleting: Boolean = false,
) {
    val matchCount: Int get() = matches.size

    /** More older matches remain on the server. */
    val canLoadMore: Boolean get() = nextCursor != null

    /** Matches grouped by local day; both days and matches stay newest-first. */
    val sections: List<MatchDateSection> get() {
        val zone = ZoneId.systemDefault()
        return matches
            .groupBy { it.playedAt.atZone(zone).toLocalDate() }
            .map { (date, list) -> MatchDateSection(date, list) }
    }

    /** The most recent day present in the list — expanded by default. */
    val newestDate: LocalDate? get() = sections.firstOrNull()?.date

    /**
     * Whether [date]'s section is expanded: honors a user override if present, otherwise
     * only the [newestDate] is expanded (older days collapse to their header).
     */
    fun isDateExpanded(date: LocalDate): Boolean = expandedDates[date] ?: (date == newestDate)

    /** Whether the viewer may edit/delete [detail]: the recorder or a moderator. */
    fun canModify(detail: MatchDetail): Boolean =
        canModerate || detail.recordedByUserId == currentUserId
}
