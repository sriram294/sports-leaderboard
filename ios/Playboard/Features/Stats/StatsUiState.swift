import Foundation

/// Calendar window used by the Stats tab. There is no weekly window: a week is too few games
/// for a confidence-adjusted rating to separate anyone.
enum StatsTimeRange: String, CaseIterable, Sendable {
    case month = "This month"
    case allTime = "All time"
}

/// Minimum games before a player can headline the win-rate record.
let minLeaderGames = 2

/// Minimum run before a streak is shown as a record.
let minRecordStreak = 2

/// Group records derived from the selected leaderboard window and match count.
struct StatsRecords: Equatable, Sendable {
    let totalMatches: Int
    /// Top entry with at least ``minLeaderGames`` games, else the top-ranked entry.
    let winLeader: LeaderboardEntry?
    let mostPoints: LeaderboardEntry?
    let mostActive: LeaderboardEntry?
    /// Highest best-ever win streak, when someone reached ``minRecordStreak``.
    let longestStreak: LeaderboardEntry?
    /// Highest current win streak, when someone is on a ``minRecordStreak``+ run.
    let currentStreak: LeaderboardEntry?

    /// Builds records from server-ordered rankings; ties break toward the higher-ranked player.
    init(rankings: [LeaderboardEntry], matchCount: Int) {
        totalMatches = matchCount
        winLeader = rankings.first { $0.gamesPlayed >= minLeaderGames } ?? rankings.first
        mostPoints = Self.firstMax(rankings, by: \.pointsFor)
        mostActive = Self.firstMax(rankings, by: \.gamesPlayed)
        longestStreak = Self.firstMax(rankings, by: \.bestStreak).flatMap { $0.bestStreak >= minRecordStreak ? $0 : nil }
        currentStreak = Self.firstMax(rankings, by: \.currentStreak).flatMap { $0.currentStreak >= minRecordStreak ? $0 : nil }
    }

    private static func firstMax(_ rows: [LeaderboardEntry], by value: KeyPath<LeaderboardEntry, Int>) -> LeaderboardEntry? {
        rows.reduce(nil) { best, row in
            guard let best else { return row }
            return row[keyPath: value] > best[keyPath: value] ? row : best
        }
    }
}

/// The recent match with the largest total-points margin, summed across sets.
struct BiggestWin: Equatable, Sendable {
    let match: MatchSummary
    let margin: Int

    /// Nil when there are no matches or every margin is zero.
    init?(matches: [MatchSummary]) {
        var best: (MatchSummary, Int)?
        for match in matches {
            let margin = abs(match.sets.reduce(0) { $0 + $1.team1Score } - match.sets.reduce(0) { $0 + $1.team2Score })
            if margin > 0, margin > (best?.1 ?? 0) { best = (match, margin) }
        }
        guard let best else { return nil }
        self.match = best.0
        self.margin = best.1
    }
}

/// Immutable state for the group Insights dashboard.
struct StatsUiState: Equatable, Sendable {
    var groupID: String?
    var groupMatchCount = 0
    var range: StatsTimeRange = .month
    var isLoading = false
    var isRefreshing = false
    var errorMessage: String?
    var hasMatches = false
    var records: StatsRecords?
    var biggestWin: BiggestWin?
    /// Who topped each completed month, newest first; served, never recomputed.
    var monthlyWinners: [MonthlyTrophy] = []
    var partnersExpanded = false
    /// Everyone on the selected leaderboard, used by the partner picker.
    var players: [LeaderboardEntry] = []
    var selectedPlayerID: String?
    var partners: [PartnerStats] = []
    var isLoadingPartners = false
    var partnersError: String?

    var selectedPlayer: LeaderboardEntry? { players.first { $0.userID == selectedPlayerID } }
}
