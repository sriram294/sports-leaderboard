import Foundation

/// Matches grouped under one local calendar day.
struct MatchDaySection: Equatable, Identifiable, Sendable {
    let day: Date
    let matches: [MatchSummary]
    var id: Date { day }
}

/// Immutable presentation state for match history and expanded detail.
struct MatchesUiState: Equatable, Sendable {
    var groupID: String?
    var groupMatchCount = 0
    var isLoading = false
    var isRefreshing = false
    var isLoadingMore = false
    var matches: [MatchSummary] = []
    var nextCursor: String?
    var mineOnly = false
    var expandedMatchID: String?
    var detail: MatchDetail?
    var isDetailLoading = false
    var errorMessage: String?
    var staleMessage: String?
    var detailErrorMessage: String?
    /// Signed-in user, used to decide whether they recorded a match.
    var currentUserID = ""
    /// Owners and admins may edit or delete any match, not just their own.
    var canModerate = false
    /// The match awaiting delete confirmation.
    var pendingDeletionID: String?
    var isDeleting = false
    var deleteErrorMessage: String?
    /// Set after a successful delete so other screens can refresh their derived data.
    var lastDeletedMatchID: String?
    /// Per-day expand/collapse overrides; days without one fall back to "newest day expanded".
    var expandedDays: [Date: Bool] = [:]
    var canLoadMore: Bool { nextCursor != nil && !isLoadingMore }

    /// Whether the viewer may edit or delete `detail`: its recorder or a moderator.
    func canModify(_ detail: MatchDetail) -> Bool {
        canModerate || detail.recordedBy.userID == currentUserID
    }
}
