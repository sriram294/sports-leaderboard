import Foundation

/// The only two supported leaderboard windows.
enum LeaderboardRange: String, CaseIterable, Identifiable, Sendable {
    case month = "This Month"
    case allTime = "All Time"
    var id: String { rawValue }
}

/// Optional client-side metric while canonical rank remains server-owned.
enum LeaderboardMetric: String, CaseIterable, Sendable {
    case rating = "Rating"
    case winRate = "Win %"
    case games = "Games"
    case pointsDifference = "Difference"

    var next: LeaderboardMetric {
        let values = Self.allCases
        return values[(values.firstIndex(of: self)! + 1) % values.count]
    }
}

/// Immutable presentation state for the Board tab.
struct BoardUiState: Equatable, Sendable {
    var groupID: String?
    var groupMemberCount = 0
    var isLoading = false
    var isRefreshing = false
    var rankings: [LeaderboardEntry] = []
    var minGamesToRank = 1
    var range: LeaderboardRange = .month
    var metric: LeaderboardMetric = .rating
    var monthName = ""
    var errorMessage: String?
    var staleMessage: String?

    var podium: [LeaderboardEntry] { Array(rankings.filter { !$0.provisional }.prefix(3)) }

    var tableRows: [LeaderboardEntry] {
        let indexed = rankings.enumerated().map { (offset: $0.offset, entry: $0.element) }
        let ranked = indexed.filter { !$0.entry.provisional }
        let provisional = indexed.filter { $0.entry.provisional }
        return (sort(ranked) + sort(provisional)).map(\.entry)
    }

    private func sort(_ rows: [(offset: Int, entry: LeaderboardEntry)]) -> [(offset: Int, entry: LeaderboardEntry)] {
        guard metric != .rating else { return rows }
        return rows.sorted { left, right in
            let comparison: ComparisonResult = switch metric {
            case .rating: .orderedSame
            case .winRate: compare(left.entry.winRate, right.entry.winRate)
            case .games: compare(left.entry.gamesPlayed, right.entry.gamesPlayed)
            case .pointsDifference: compare(left.entry.pointsDifference, right.entry.pointsDifference)
            }
            return comparison == .orderedSame ? left.offset < right.offset : comparison == .orderedDescending
        }
    }

    private func compare<T: Comparable>(_ left: T, _ right: T) -> ComparisonResult {
        if left == right { return .orderedSame }
        return left > right ? .orderedDescending : .orderedAscending
    }
}
