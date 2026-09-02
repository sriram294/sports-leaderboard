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
    var canLoadMore: Bool { nextCursor != nil && !isLoadingMore }
}
