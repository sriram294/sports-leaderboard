import Foundation

/// One canonical server-ranked leaderboard entry.
struct LeaderboardEntry: Codable, Equatable, Identifiable, Sendable {
    let rank: Int
    let userID: String
    let displayName: String
    let photoURL: String?
    let avatarID: String?
    let avatarColor: String
    let gamesPlayed: Int
    let wins: Int
    let losses: Int
    let pointsFor: Int
    let pointsAgainst: Int
    let winRate: Double
    let currentStreak: Int
    let bestStreak: Int
    let rating: Double
    let provisional: Bool
    let recentForm: [Bool]

    var id: String { userID }
    var pointsDifference: Int { pointsFor - pointsAgainst }

    enum CodingKeys: String, CodingKey {
        case rank
        case userID = "userId"
        case displayName
        case photoURL = "photoUrl"
        case avatarID = "avatarId"
        case avatarColor
        case gamesPlayed, wins, losses, pointsFor, pointsAgainst, winRate
        case currentStreak, bestStreak, rating, provisional, recentForm
    }
}

/// Complete leaderboard response, including the provisional threshold.
struct Leaderboard: Codable, Equatable, Sendable {
    let rankings: [LeaderboardEntry]
    let minGamesToRank: Int
}
