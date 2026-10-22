import Foundation
#if canImport(FoundationNetworking)
import FoundationNetworking
#endif

/// API boundary for profile, stats, attendance, trophies, and account appearance.
protocol ProfileRepository: Sendable {
    func stats(groupID: String, userID: String) async throws -> PlayerStats
    /// Partners within `window`, or all time when nil.
    func partners(groupID: String, userID: String, window: DateInterval?) async throws -> [PartnerStats]
    /// Distinct instants the player was in a match within `window`.
    func attendance(groupID: String, userID: String, window: DateInterval) async throws -> [Date]
    /// The group's monthly winners, newest first.
    func trophies(groupID: String, limit: Int) async throws -> [MonthlyTrophy]
    func updateDisplayName(_ name: String) async throws -> AuthenticatedUser
    /// Selects one of the bundled default avatars; the server clears any uploaded photo.
    func updateAvatar(_ avatarID: String) async throws -> AuthenticatedUser
    /// Uploads a replacement profile photo.
    func uploadPhoto(_ data: Data, mimeType: String) async throws -> AuthenticatedUser
}

/// How many completed months the Stats "Monthly winners" card shows.
let groupTrophyLimit = 6

/// Live implementation of the profile, stats, and user contracts.
actor LiveProfileRepository: ProfileRepository {
    private let api: AuthorizedAPI
    private let decoder = PlayboardJSON.decoder()
    private let encoder = PlayboardJSON.encoder()

    init(apiClient: any APIClient, baseURL: URL, accessToken: String, refreshAccessToken: (@Sendable () async throws -> String)? = nil) {
        api = AuthorizedAPI(apiClient: apiClient, baseURL: baseURL, accessToken: accessToken, refreshAccessToken: refreshAccessToken)
    }

    func stats(groupID: String, userID: String) async throws -> PlayerStats {
        try await get("groups/\(groupID)/members/\(userID)/stats", as: PlayerStats.self)
    }

    func partners(groupID: String, userID: String, window: DateInterval?) async throws -> [PartnerStats] {
        try await get("groups/\(groupID)/members/\(userID)/stats/partners", query: Self.windowQuery(window), as: [PartnerStats].self)
    }

    func attendance(groupID: String, userID: String, window: DateInterval) async throws -> [Date] {
        let response = try await get(
            "groups/\(groupID)/members/\(userID)/attendance", query: Self.windowQuery(window), as: AttendanceResponse.self)
        return response.playedAt.compactMap(PlayboardJSON.parseDate)
    }

    func trophies(groupID: String, limit: Int) async throws -> [MonthlyTrophy] {
        try await get("groups/\(groupID)/trophies", query: [URLQueryItem(name: "limit", value: String(limit))], as: [MonthlyTrophy].self)
            .filter(\.isValid)
    }

    func updateDisplayName(_ name: String) async throws -> AuthenticatedUser {
        let body = try encoder.encode(NameBody(displayName: name.trimmingCharacters(in: .whitespacesAndNewlines)))
        return try decode(try await api.send(path: "users/me", method: "PATCH", body: body), as: AuthenticatedUser.self)
    }

    func updateAvatar(_ avatarID: String) async throws -> AuthenticatedUser {
        let body = try encoder.encode(AvatarBody(avatarId: avatarID))
        return try decode(try await api.send(path: "users/me/avatar", method: "PATCH", body: body), as: AuthenticatedUser.self)
    }

    func uploadPhoto(_ data: Data, mimeType: String) async throws -> AuthenticatedUser {
        let boundary = "Playboard-\(UUID().uuidString)"
        let fileExtension = mimeType == "image/png" ? "png" : "jpg"
        var body = Data()
        body.append(Data("--\(boundary)\r\n".utf8))
        body.append(Data("Content-Disposition: form-data; name=\"file\"; filename=\"avatar.\(fileExtension)\"\r\n".utf8))
        body.append(Data("Content-Type: \(mimeType)\r\n\r\n".utf8))
        body.append(data)
        body.append(Data("\r\n--\(boundary)--\r\n".utf8))
        let response = try await api.send(
            path: "users/me/photo", method: "POST", body: body, contentType: "multipart/form-data; boundary=\(boundary)")
        return try decode(response, as: AuthenticatedUser.self)
    }

    private func get<Response: Decodable>(_ path: String, query: [URLQueryItem] = [], as type: Response.Type) async throws -> Response {
        try decode(try await api.send(path: path, method: "GET", query: query), as: type)
    }

    private func decode<Response: Decodable>(_ response: APIResponse, as type: Response.Type) throws -> Response {
        guard (200..<300).contains(response.statusCode) else {
            throw response.statusCode == 403 ? GroupRepositoryError.permissionDenied : GroupRepositoryError.invalidResponse
        }
        do { return try decoder.decode(type, from: response.data) }
        catch { throw GroupRepositoryError.invalidResponse }
    }

    private static func windowQuery(_ window: DateInterval?) -> [URLQueryItem] {
        guard let window else { return [] }
        return [
            URLQueryItem(name: "from", value: PlayboardJSON.queryString(window.start)),
            URLQueryItem(name: "to", value: PlayboardJSON.queryString(window.end))
        ]
    }
}

/// Maps transport and API failures from profile flows to user-facing copy.
func profileErrorMessage(_ error: Error) -> String {
    switch error {
    case AuthorizedAPIError.offline: GroupRepositoryError.offline.message
    case AuthorizedAPIError.unauthorized: "Your session expired. Sign in again to continue."
    case let error as GroupRepositoryError: error.message
    default: GroupRepositoryError.invalidResponse.message
    }
}

private struct AttendanceResponse: Decodable { let playedAt: [String] }
private struct NameBody: Encodable { let displayName: String }
private struct AvatarBody: Encodable { let avatarId: String }
