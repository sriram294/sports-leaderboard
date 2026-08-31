import Foundation
#if canImport(FoundationNetworking)
import FoundationNetworking
#endif

/// Network boundary for existing leaderboard data.
protocol LeaderboardRepository: Sendable {
    func leaderboard(groupID: String, window: DateInterval?) async throws -> Leaderboard
}

/// Existing-API leaderboard repository with one S01 token refresh retry.
actor LiveLeaderboardRepository: LeaderboardRepository {
    private let apiClient: any APIClient
    private let baseURL: URL
    private var accessToken: String
    private let refreshAccessToken: (@Sendable () async throws -> String)?
    private let decoder = JSONDecoder()

    init(
        apiClient: any APIClient,
        baseURL: URL,
        accessToken: String,
        refreshAccessToken: (@Sendable () async throws -> String)? = nil
    ) {
        self.apiClient = apiClient
        self.baseURL = baseURL
        self.accessToken = accessToken
        self.refreshAccessToken = refreshAccessToken
    }

    func leaderboard(groupID: String, window: DateInterval?) async throws -> Leaderboard {
        var components = URLComponents(url: baseURL.appendingPathComponent("groups/\(groupID)/leaderboard"), resolvingAgainstBaseURL: false)
        if let window {
            components?.queryItems = [
                URLQueryItem(name: "from", value: window.start.ISO8601Format()),
                URLQueryItem(name: "to", value: window.end.ISO8601Format())
            ]
        }
        guard let url = components?.url else { throw GroupRepositoryError.invalidResponse }
        var request = URLRequest(url: url)
        request.httpMethod = "GET"
        request.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")

        var response = try await apiResponse(for: request)
        if response.statusCode == 401, let refreshAccessToken {
            do { accessToken = try await refreshAccessToken() }
            catch { throw GroupRepositoryError.permissionDenied }
            request.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")
            response = try await apiResponse(for: request)
        }
        guard (200..<300).contains(response.statusCode) else {
            throw response.statusCode == 403 ? GroupRepositoryError.permissionDenied : GroupRepositoryError.invalidResponse
        }
        do { return try decoder.decode(Leaderboard.self, from: response.data) }
        catch { throw GroupRepositoryError.invalidResponse }
    }

    private func apiResponse(for request: URLRequest) async throws -> APIResponse {
        do { return try await apiClient.response(for: request) }
        catch is CancellationError { throw CancellationError() }
        catch { throw GroupRepositoryError.offline }
    }
}
