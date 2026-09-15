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
    let rating: Double?
    let provisional: Bool
    let recentForm: [Bool]
    let algorithmVersion: String?
    let ratingPeriod: String?
    let uniquePartners: Int?
    let maxPartnerShare: Double?
    let provisionalReason: String?
    let limitedPartnerVariety: Bool

    var id: String { userID }

    init(rank: Int, userID: String, displayName: String, photoURL: String?, avatarID: String?, avatarColor: String,
         gamesPlayed: Int, wins: Int, losses: Int, pointsFor: Int, pointsAgainst: Int, winRate: Double,
         currentStreak: Int = 0, bestStreak: Int = 0, rating: Double?, provisional: Bool = false,
         recentForm: [Bool] = [], algorithmVersion: String? = nil, ratingPeriod: String? = nil,
         uniquePartners: Int? = nil, maxPartnerShare: Double? = nil, provisionalReason: String? = nil) {
        self.rank = rank; self.userID = userID; self.displayName = displayName; self.photoURL = photoURL; self.avatarID = avatarID; self.avatarColor = avatarColor
        self.gamesPlayed = gamesPlayed; self.wins = wins; self.losses = losses; self.pointsFor = pointsFor; self.pointsAgainst = pointsAgainst; self.winRate = winRate
        self.currentStreak = currentStreak; self.bestStreak = bestStreak; self.rating = rating; self.provisional = provisional; self.recentForm = recentForm
        self.algorithmVersion = algorithmVersion; self.ratingPeriod = ratingPeriod; self.uniquePartners = uniquePartners; self.maxPartnerShare = maxPartnerShare; self.provisionalReason = provisionalReason; self.limitedPartnerVariety = false
    }
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
        case algorithmVersion, ratingPeriod, uniquePartners, maxPartnerShare, provisionalReason, limitedPartnerVariety
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        rank = try c.decode(Int.self, forKey: .rank); userID = try c.decode(String.self, forKey: .userID)
        displayName = try c.decode(String.self, forKey: .displayName); photoURL = try c.decodeIfPresent(String.self, forKey: .photoURL)
        avatarID = try c.decodeIfPresent(String.self, forKey: .avatarID); avatarColor = try c.decode(String.self, forKey: .avatarColor)
        gamesPlayed = try c.decode(Int.self, forKey: .gamesPlayed); wins = try c.decode(Int.self, forKey: .wins); losses = try c.decode(Int.self, forKey: .losses)
        pointsFor = try c.decode(Int.self, forKey: .pointsFor); pointsAgainst = try c.decode(Int.self, forKey: .pointsAgainst); winRate = try c.decode(Double.self, forKey: .winRate)
        currentStreak = try c.decodeIfPresent(Int.self, forKey: .currentStreak) ?? 0; bestStreak = try c.decodeIfPresent(Int.self, forKey: .bestStreak) ?? 0
        rating = try c.decodeIfPresent(Double.self, forKey: .rating); provisional = try c.decodeIfPresent(Bool.self, forKey: .provisional) ?? false
        recentForm = try c.decodeIfPresent([Bool].self, forKey: .recentForm) ?? []; algorithmVersion = try c.decodeIfPresent(String.self, forKey: .algorithmVersion)
        ratingPeriod = try c.decodeIfPresent(String.self, forKey: .ratingPeriod); uniquePartners = try c.decodeIfPresent(Int.self, forKey: .uniquePartners)
        maxPartnerShare = try c.decodeIfPresent(Double.self, forKey: .maxPartnerShare); provisionalReason = try c.decodeIfPresent(String.self, forKey: .provisionalReason)
        limitedPartnerVariety = try c.decodeIfPresent(Bool.self, forKey: .limitedPartnerVariety) ?? false
    }
}

/// Complete leaderboard response, including the provisional threshold.
struct Leaderboard: Codable, Equatable, Sendable {
    let rankings: [LeaderboardEntry]
    let minGamesToRank: Int
    let algorithmVersion: String?
    let ratingPeriod: String?

    init(rankings: [LeaderboardEntry], minGamesToRank: Int, algorithmVersion: String? = nil, ratingPeriod: String? = nil) {
        self.rankings = rankings; self.minGamesToRank = minGamesToRank; self.algorithmVersion = algorithmVersion; self.ratingPeriod = ratingPeriod
    }

    enum CodingKeys: String, CodingKey { case rankings, minGamesToRank, algorithmVersion, ratingPeriod }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        rankings = try c.decode([LeaderboardEntry].self, forKey: .rankings)
        minGamesToRank = try c.decodeIfPresent(Int.self, forKey: .minGamesToRank) ?? 1
        algorithmVersion = try c.decodeIfPresent(String.self, forKey: .algorithmVersion)
        ratingPeriod = try c.decodeIfPresent(String.self, forKey: .ratingPeriod)
    }
}
