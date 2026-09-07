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
    let algorithmVersion: String? = nil
    let ratingPeriod: String? = nil
    let uniquePartners: Int?
    let maxPartnerShare: Double?
    let provisionalReason: String?

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
        case algorithmVersion, ratingPeriod, uniquePartners, maxPartnerShare, provisionalReason
    }
}

/// Complete leaderboard response, including the provisional threshold.
struct Leaderboard: Codable, Equatable, Sendable {
    let rankings: [LeaderboardEntry]
    let minGamesToRank: Int
    let algorithmVersion: String? = nil
    let ratingPeriod: String? = nil
}
