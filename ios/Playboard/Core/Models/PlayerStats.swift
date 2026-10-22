import Foundation

/// A month a player topped the group's leaderboard, frozen when that month closed.
struct MonthlyTrophy: Codable, Equatable, Identifiable, Sendable {
    /// ISO `yyyy-MM`; the award belongs to the month, not a day in it.
    let month: String
    let userID: String
    let displayName: String
    let photoURL: String?
    let avatarID: String?
    let avatarColor: String
    /// Winning stats as they stood when the month closed; nil when the backend did not record them.
    let rating: Double?
    let gamesPlayed: Int?
    let wins: Int?

    var id: String { "\(month)-\(userID)" }

    init(month: String, userID: String, displayName: String, photoURL: String? = nil, avatarID: String? = nil,
         avatarColor: String, rating: Double? = nil, gamesPlayed: Int? = nil, wins: Int? = nil) {
        self.month = month; self.userID = userID; self.displayName = displayName; self.photoURL = photoURL
        self.avatarID = avatarID; self.avatarColor = avatarColor; self.rating = rating; self.gamesPlayed = gamesPlayed; self.wins = wins
    }

    enum CodingKeys: String, CodingKey {
        case month, userID = "userId", displayName, photoURL = "photoUrl", avatarID = "avatarId", avatarColor, rating, gamesPlayed, wins
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        month = try c.decodeIfPresent(String.self, forKey: .month) ?? ""
        userID = try c.decodeIfPresent(String.self, forKey: .userID) ?? ""
        displayName = try c.decodeIfPresent(String.self, forKey: .displayName) ?? ""
        photoURL = try c.decodeIfPresent(String.self, forKey: .photoURL)
        avatarID = try c.decodeIfPresent(String.self, forKey: .avatarID)
        avatarColor = try c.decodeIfPresent(String.self, forKey: .avatarColor) ?? "#000000"
        rating = try c.decodeIfPresent(Double.self, forKey: .rating)
        gamesPlayed = try c.decodeIfPresent(Int.self, forKey: .gamesPlayed)
        wins = try c.decodeIfPresent(Int.self, forKey: .wins)
    }

    /// Whether the record is complete enough to show; a pre-trophy backend can send blanks.
    var isValid: Bool { !month.isEmpty && !userID.isEmpty }

    /// Short month label such as "Sep" for shelves and tiles.
    var shortMonthLabel: String { PlayboardMonth.label(month, format: "MMM") }

    /// Long month label such as "September 2026".
    var longMonthLabel: String { PlayboardMonth.label(month, format: "MMMM yyyy") }
}

/// Formats `yyyy-MM` month keys returned by the API.
enum PlayboardMonth {
    /// Formats a `yyyy-MM` key with the given pattern, falling back to the raw key.
    static func label(_ key: String, format: String) -> String {
        let input = DateFormatter()
        input.locale = Locale(identifier: "en_US_POSIX")
        input.timeZone = TimeZone(identifier: "UTC")
        input.dateFormat = "yyyy-MM"
        guard let date = input.date(from: key) else { return key }
        let output = DateFormatter()
        output.timeZone = TimeZone(identifier: "UTC")
        output.dateFormat = format
        return output.string(from: date)
    }
}

/// A player's group-scoped profile and statistics payload.
struct PlayerStats: Codable, Equatable, Sendable {
    /// One captured month in chronological order; rank is nil when the player did not qualify.
    struct MonthlyFinish: Codable, Equatable, Sendable, Identifiable {
        let month: String
        let rank: Int?
        let qualifiedPlayers: Int
        var id: String { month }
    }

    let userID: String
    let displayName: String
    let photoURL: String?
    let avatarID: String?
    let avatarColor: String
    let matchesPlayed: Int
    let wins: Int
    let losses: Int
    let pointsFor: Int
    let pointsAgainst: Int
    let winRate: Double
    let currentStreak: Int
    let bestStreak: Int
    /// Newest first, capped server-side.
    let recentMatches: [MatchSummary]
    /// Months this player topped the group, newest first.
    let trophies: [MonthlyTrophy]
    let monthlyFinishes: [MonthlyFinish]

    init(userID: String, displayName: String, photoURL: String?, avatarID: String? = nil, avatarColor: String,
         matchesPlayed: Int, wins: Int, losses: Int, pointsFor: Int, pointsAgainst: Int, winRate: Double,
         currentStreak: Int, bestStreak: Int, recentMatches: [MatchSummary] = [], trophies: [MonthlyTrophy] = [],
         monthlyFinishes: [MonthlyFinish] = []) {
        self.userID = userID; self.displayName = displayName; self.photoURL = photoURL; self.avatarID = avatarID
        self.avatarColor = avatarColor; self.matchesPlayed = matchesPlayed; self.wins = wins; self.losses = losses
        self.pointsFor = pointsFor; self.pointsAgainst = pointsAgainst; self.winRate = winRate
        self.currentStreak = currentStreak; self.bestStreak = bestStreak; self.recentMatches = recentMatches
        self.trophies = trophies; self.monthlyFinishes = monthlyFinishes
    }

    enum CodingKeys: String, CodingKey {
        case userID = "userId", displayName, photoURL = "photoUrl", avatarID = "avatarId", avatarColor
        case matchesPlayed, wins, losses, pointsFor, pointsAgainst, winRate
        case currentStreak, bestStreak, recentMatches, trophies, monthlyFinishes
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        userID = try c.decode(String.self, forKey: .userID)
        displayName = try c.decode(String.self, forKey: .displayName)
        photoURL = try c.decodeIfPresent(String.self, forKey: .photoURL)
        avatarID = try c.decodeIfPresent(String.self, forKey: .avatarID)
        avatarColor = try c.decode(String.self, forKey: .avatarColor)
        matchesPlayed = try c.decode(Int.self, forKey: .matchesPlayed)
        wins = try c.decode(Int.self, forKey: .wins)
        losses = try c.decode(Int.self, forKey: .losses)
        pointsFor = try c.decode(Int.self, forKey: .pointsFor)
        pointsAgainst = try c.decode(Int.self, forKey: .pointsAgainst)
        winRate = try c.decode(Double.self, forKey: .winRate)
        currentStreak = try c.decodeIfPresent(Int.self, forKey: .currentStreak) ?? 0
        bestStreak = try c.decodeIfPresent(Int.self, forKey: .bestStreak) ?? 0
        recentMatches = try c.decodeIfPresent([MatchSummary].self, forKey: .recentMatches) ?? []
        trophies = (try c.decodeIfPresent([MonthlyTrophy].self, forKey: .trophies) ?? []).filter(\.isValid)
        monthlyFinishes = try c.decodeIfPresent([MonthlyFinish].self, forKey: .monthlyFinishes) ?? []
    }
}

/// One player's record alongside a partner.
struct PartnerStats: Codable, Equatable, Sendable, Identifiable {
    let userID: String
    let displayName: String
    let photoURL: String?
    let avatarID: String?
    let avatarColor: String
    let gamesTogether: Int
    let winsTogether: Int
    let winRate: Double
    var id: String { userID }

    init(userID: String, displayName: String, photoURL: String? = nil, avatarID: String? = nil, avatarColor: String,
         gamesTogether: Int, winsTogether: Int, winRate: Double) {
        self.userID = userID; self.displayName = displayName; self.photoURL = photoURL; self.avatarID = avatarID
        self.avatarColor = avatarColor; self.gamesTogether = gamesTogether; self.winsTogether = winsTogether; self.winRate = winRate
    }

    enum CodingKeys: String, CodingKey {
        case userID = "userId", displayName, photoURL = "photoUrl", avatarID = "avatarId", avatarColor, gamesTogether, winsTogether, winRate
    }
}

/// One recent match framed from a single player's point of view.
struct RecentMatchRow: Equatable, Identifiable, Sendable {
    let matchID: String
    let playedAt: Date
    let isWin: Bool
    let partnerNames: String
    let opponentNames: String
    /// Set scores ordered so the viewed player's team comes first.
    let scores: [(mine: Int, theirs: Int)]

    var id: String { matchID }

    static func == (lhs: RecentMatchRow, rhs: RecentMatchRow) -> Bool {
        lhs.matchID == rhs.matchID && lhs.playedAt == rhs.playedAt && lhs.isWin == rhs.isWin
            && lhs.partnerNames == rhs.partnerNames && lhs.opponentNames == rhs.opponentNames
            && lhs.scores.map(\.mine) == rhs.scores.map(\.mine) && lhs.scores.map(\.theirs) == rhs.scores.map(\.theirs)
    }

    /// Builds the row for `userID`, or nil when that player did not play the match.
    init?(match: MatchSummary, userID: String) {
        guard let mine = match.teams.first(where: { team in team.players.contains { $0.userID == userID } }) else { return nil }
        let opponents = match.teams.first { $0.teamNo != mine.teamNo }
        matchID = match.id
        playedAt = match.playedAt
        isWin = mine.isWinner
        partnerNames = mine.players.filter { $0.userID != userID }.map(\.displayName).joined(separator: " & ")
        opponentNames = (opponents?.players ?? []).map(\.displayName).joined(separator: " & ")
        scores = match.sets.sorted { $0.setNo < $1.setNo }.map { set in
            mine.teamNo == 1 ? (set.team1Score, set.team2Score) : (set.team2Score, set.team1Score)
        }
    }
}
