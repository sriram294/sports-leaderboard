import Foundation
#if canImport(FoundationNetworking)
import FoundationNetworking
#endif

/// Transport failures shared by repositories built on ``AuthorizedAPI``.
enum AuthorizedAPIError: Error, Equatable, Sendable {
    /// The request never produced an HTTP response.
    case offline
    /// The session could not be refreshed after a 401.
    case unauthorized
}

/// Bearer-token request helper that refreshes once and retries when the API answers 401.
actor AuthorizedAPI {
    private let apiClient: any APIClient
    private let baseURL: URL
    private var accessToken: String
    private let refreshAccessToken: (@Sendable () async throws -> String)?

    init(apiClient: any APIClient, baseURL: URL, accessToken: String, refreshAccessToken: (@Sendable () async throws -> String)? = nil) {
        self.apiClient = apiClient
        self.baseURL = baseURL
        self.accessToken = accessToken
        self.refreshAccessToken = refreshAccessToken
    }

    /// Sends one request relative to the API base URL and returns the raw response.
    func send(
        path: String,
        method: String,
        query: [URLQueryItem] = [],
        body: Data? = nil,
        contentType: String = "application/json"
    ) async throws -> APIResponse {
        var components = URLComponents(url: baseURL.appendingPathComponent(path), resolvingAgainstBaseURL: false)
        components?.queryItems = query.isEmpty ? nil : query
        guard let url = components?.url else { throw AuthorizedAPIError.offline }
        var request = URLRequest(url: url)
        request.httpMethod = method
        request.httpBody = body
        request.setValue(contentType, forHTTPHeaderField: "Content-Type")
        request.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")

        let first = try await transport(request)
        guard first.statusCode == 401, let refreshAccessToken else { return first }
        do { accessToken = try await refreshAccessToken() }
        catch { throw AuthorizedAPIError.unauthorized }
        request.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")
        return try await transport(request)
    }

    private func transport(_ request: URLRequest) async throws -> APIResponse {
        do { return try await apiClient.response(for: request) }
        catch is CancellationError { throw CancellationError() }
        catch { throw AuthorizedAPIError.offline }
    }
}
