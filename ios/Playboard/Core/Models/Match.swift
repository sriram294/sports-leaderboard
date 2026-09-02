import Foundation

/// One player embedded in a match response.
struct MatchPlayer: Codable, Equatable, Identifiable, Sendable {
    let userID: String
    let displayName: String
    let avatarColor: String
    let photoURL: String?
    let avatarID: String?
    var id: String { userID }
    enum CodingKeys: String, CodingKey {
        case userID = "userId"
        case displayName, avatarColor
        case photoURL = "photoUrl"
        case avatarID = "avatarId"
    }
}

/// One numbered team and its server-owned winner flag.
struct MatchTeam: Codable, Equatable, Identifiable, Sendable {
    let teamNo: Int
    let isWinner: Bool
    let players: [MatchPlayer]
    var id: Int { teamNo }
}

/// One set score in team-one/team-two order.
struct MatchSet: Codable, Equatable, Identifiable, Sendable {
    let setNo: Int
    let team1Score: Int
    let team2Score: Int
    var id: Int { setNo }
}

/// Lightweight match returned by the paginated history endpoint.
struct MatchSummary: Codable, Equatable, Identifiable, Sendable {
    let id: String
    let playedAt: Date
    let teams: [MatchTeam]
    let sets: [MatchSet]
    func team(_ number: Int) -> MatchTeam? { teams.first { $0.teamNo == number } }
    var winningTeam: MatchTeam? { teams.first(where: \.isWinner) }
}

/// User who originally recorded a match.
struct MatchActor: Codable, Equatable, Sendable {
    let userID: String
    let displayName: String
    enum CodingKeys: String, CodingKey { case userID = "userId", displayName }
}

/// One append-only match audit event.
struct MatchEvent: Codable, Equatable, Identifiable, Sendable {
    let userID: String
    let displayName: String
    let action: String
    let createdAt: Date
    var id: String { "\(userID)-\(createdAt.timeIntervalSince1970)-\(action)" }
    enum CodingKeys: String, CodingKey { case userID = "userId", displayName, action, createdAt }
}

/// Expanded history record fetched on demand.
struct MatchDetail: Codable, Equatable, Identifiable, Sendable {
    let id: String
    let playedAt: Date
    let teams: [MatchTeam]
    let sets: [MatchSet]
    let recordedBy: MatchActor
    let recordedAt: Date
    let events: [MatchEvent]
    func team(_ number: Int) -> MatchTeam? { teams.first { $0.teamNo == number } }
    var winningTeam: MatchTeam? { teams.first(where: \.isWinner) }
}

/// Cursor page returned by match history.
struct MatchPage: Codable, Equatable, Sendable {
    let matches: [MatchSummary]
    let nextCursor: String?
}

/// Existing backend body shared by create and full-replace edit operations.
struct RecordMatchRequest: Codable, Equatable, Sendable {
    struct Team: Codable, Equatable, Sendable { let teamNo: Int; let playerIds: [String] }
    struct SetScore: Codable, Equatable, Sendable { let setNo: Int; let team1Score: Int; let team2Score: Int }
    let playedAt: Date
    let teams: [Team]
    let sets: [SetScore]
    let winningTeamNo: Int
}
