import Foundation
#if canImport(FoundationNetworking)
import FoundationNetworking
#endif

enum MatchRepositoryError: Error, Equatable, Sendable {
    case invalidTeams
    case invalidScores
    case forbidden
    case requestConflict
    case offline
    case invalidResponse

    var message: String {
        switch self {
        case .invalidTeams: "Choose four different active players."
        case .invalidScores: "Enter complete, non-tied set scores and a consistent winner."
        case .forbidden: "Your role does not allow that match action."
        case .requestConflict: "This attempt changed after it reached the server. Review and record it again."
        case .offline: "Playboard could not reach the server. Try again."
        case .invalidResponse: "Playboard received an unexpected match response."
        }
    }
}

protocol MatchRepository: Sendable {
    func matches(groupID: String, cursor: String?, mine: Bool) async throws -> MatchPage
    func detail(groupID: String, matchID: String) async throws -> MatchDetail
    func record(groupID: String, request: RecordMatchRequest, requestID: String) async throws -> MatchDetail
}

actor LiveMatchRepository: MatchRepository {
    private let apiClient: any APIClient
    private let baseURL: URL
    private var accessToken: String
    private let refreshAccessToken: (@Sendable () async throws -> String)?
    private let encoder: JSONEncoder
    private let decoder: JSONDecoder

    init(apiClient: any APIClient, baseURL: URL, accessToken: String, refreshAccessToken: (@Sendable () async throws -> String)? = nil) {
        self.apiClient = apiClient
        self.baseURL = baseURL
        self.accessToken = accessToken
        self.refreshAccessToken = refreshAccessToken
        encoder = JSONEncoder()
        encoder.dateEncodingStrategy = .iso8601
        decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .custom { decoder in
            let value = try decoder.singleValueContainer().decode(String.self)
            return try Date(value, strategy: .iso8601)
        }
    }

    func matches(groupID: String, cursor: String?, mine: Bool) async throws -> MatchPage {
        var query = [URLQueryItem(name: "limit", value: "20")]
        if let cursor { query.append(URLQueryItem(name: "cursor", value: cursor)) }
        if mine { query.append(URLQueryItem(name: "mine", value: "true")) }
        return try await send(path: "groups/\(groupID)/matches", method: "GET", query: query, response: MatchPage.self)
    }
    func detail(groupID: String, matchID: String) async throws -> MatchDetail {
        try await send(path: "groups/\(groupID)/matches/\(matchID)", method: "GET", response: MatchDetail.self)
    }

    func record(groupID: String, request: RecordMatchRequest, requestID: String) async throws -> MatchDetail {
        try await send(
            path: "groups/\(groupID)/matches",
            method: "POST",
            body: request,
            requestID: requestID,
            response: MatchDetail.self
        )
    }

    private func send<Response: Decodable>(
        path: String,
        method: String,
        query: [URLQueryItem] = [],
        response: Response.Type
    ) async throws -> Response {
        try await perform(request(path: path, method: method, query: query), response: response)
    }

    private func send<Body: Encodable, Response: Decodable>(
        path: String,
        method: String,
        body: Body,
        requestID: String,
        response: Response.Type
    ) async throws -> Response {
        var value = request(path: path, method: method)
        value.httpBody = try encoder.encode(body)
        value.setValue(requestID, forHTTPHeaderField: "Idempotency-Key")
        return try await perform(value, response: response)
    }

    private func request(path: String, method: String, query: [URLQueryItem] = []) -> URLRequest {
        var components = URLComponents(
            url: baseURL.appendingPathComponent(path),
            resolvingAgainstBaseURL: false
        )!
        components.queryItems = query.isEmpty ? nil : query
        var value = URLRequest(url: components.url!)
        value.httpMethod = method
        value.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")
        value.setValue("application/json", forHTTPHeaderField: "Content-Type")
        return value
    }

    private func perform<Response: Decodable>(_ originalRequest: URLRequest, response: Response.Type) async throws -> Response {
        var request = originalRequest
        var result = try await transport(request)
        if result.statusCode == 401, let refreshAccessToken {
            do { accessToken = try await refreshAccessToken() }
            catch { throw MatchRepositoryError.forbidden }
            request.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")
            result = try await transport(request)
        }
        guard (200..<300).contains(result.statusCode) else { throw map(result) }
        do { return try decoder.decode(Response.self, from: result.data) }
        catch { throw MatchRepositoryError.invalidResponse }
    }

    private func transport(_ request: URLRequest) async throws -> APIResponse {
        do { return try await apiClient.response(for: request) }
        catch is CancellationError { throw CancellationError() }
        catch { throw MatchRepositoryError.offline }
    }

    private func map(_ response: APIResponse) -> MatchRepositoryError {
        let code = (try? decoder.decode(MatchProblem.self, from: response.data))?.code
        return switch code {
        case "MATCH_INVALID_TEAMS": .invalidTeams
        case "MATCH_INVALID_SCORES": .invalidScores
        case "MATCH_EDIT_FORBIDDEN": .forbidden
        case "IDEMPOTENCY_KEY_REUSED": .requestConflict
        default: response.statusCode == 403 ? .forbidden : .invalidResponse
        }
    }
}
private struct MatchProblem: Decodable { let code: String? }

protocol MatchRequestIDGenerating: Sendable { func next() -> String }
struct UUIDMatchRequestIDGenerator: MatchRequestIDGenerating { func next() -> String { UUID().uuidString.lowercased() } }
