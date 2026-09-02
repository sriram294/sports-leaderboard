import Foundation

/// A player's group-scoped profile and statistics payload.
struct PlayerStats: Codable, Equatable, Sendable {
    struct MonthlyFinish: Codable, Equatable, Sendable, Identifiable {
        let month: String
        let rank: Int?
        let qualifiedPlayers: Int
        var id: String { month }
    }

    let userID: String
    let displayName: String
    let photoURL: String?
    let avatarColor: String
    let matchesPlayed: Int
    let wins: Int
    let losses: Int
    let pointsFor: Int
    let pointsAgainst: Int
    let winRate: Double
    let currentStreak: Int
    let bestStreak: Int
    let recentMatches: [MatchSummary]
    let monthlyFinishes: [MonthlyFinish]

    enum CodingKeys: String, CodingKey {
        case userID = "userId", displayName, photoURL = "photoUrl", avatarColor
        case matchesPlayed, wins, losses, pointsFor, pointsAgainst, winRate
        case currentStreak, bestStreak, recentMatches, monthlyFinishes
    }
}

/// One player's best-partner summary.
struct PartnerStats: Codable, Equatable, Sendable, Identifiable {
    let userID: String
    let displayName: String
    let avatarColor: String
    let gamesTogether: Int
    let winsTogether: Int
    let winRate: Double
    var id: String { userID }
    enum CodingKeys: String, CodingKey {
        case userID = "userId", displayName, avatarColor, gamesTogether, winsTogether, winRate
    }
}

/// API boundary for profile, stats, and appearance-related data.
protocol ProfileRepository: Sendable {
    func stats(groupID: String, userID: String) async throws -> PlayerStats
    func partners(groupID: String, userID: String) async throws -> [PartnerStats]
    func updateDisplayName(_ name: String) async throws -> AuthenticatedUser
}

/// Live implementation of the existing profile and player-stat contracts.
actor LiveProfileRepository: ProfileRepository {
    private let apiClient: any APIClient
    private let baseURL: URL
    private var accessToken: String
    private let refreshAccessToken: (@Sendable () async throws -> String)?
    private let decoder: JSONDecoder
    private let encoder = JSONEncoder()

    init(apiClient: any APIClient, baseURL: URL, accessToken: String, refreshAccessToken: (@Sendable () async throws -> String)? = nil) {
        self.apiClient = apiClient; self.baseURL = baseURL; self.accessToken = accessToken; self.refreshAccessToken = refreshAccessToken
        decoder = JSONDecoder(); decoder.dateDecodingStrategy = .iso8601
    }

    func stats(groupID: String, userID: String) async throws -> PlayerStats {
        try await send(path: "groups/\(groupID)/members/\(userID)/stats", method: "GET", body: nil, response: PlayerStats.self)
    }

    func partners(groupID: String, userID: String) async throws -> [PartnerStats] {
        try await send(path: "groups/\(groupID)/members/\(userID)/stats/partners", method: "GET", body: nil, response: [PartnerStats].self)
    }

    func updateDisplayName(_ name: String) async throws -> AuthenticatedUser {
        try await send(path: "users/me", method: "PATCH", body: NameBody(displayName: name.trimmingCharacters(in: .whitespacesAndNewlines)), response: AuthenticatedUser.self)
    }

    private func send<Body: Encodable, Response: Decodable>(path: String, method: String, body: Body?, response: Response.Type) async throws -> Response {
        var request = URLRequest(url: baseURL.appendingPathComponent(path)); request.httpMethod = method
        request.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization"); request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        if let body { request.httpBody = try encoder.encode(body) }
        var result = try await apiClient.response(for: request)
        if result.statusCode == 401, let refreshAccessToken { accessToken = try await refreshAccessToken(); request.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization"); result = try await apiClient.response(for: request) }
        guard (200..<300).contains(result.statusCode) else { throw GroupRepositoryError.invalidResponse }
        do { return try decoder.decode(response, from: result.data) } catch { throw GroupRepositoryError.invalidResponse }
    }
}

private struct NameBody: Encodable { let displayName: String }
