import Foundation
#if canImport(FoundationNetworking)
import FoundationNetworking
#endif

/// HTTP result retained by repositories so they can map stable API errors.
struct APIResponse: Sendable {
    let data: Data
    let statusCode: Int
}

/// Network boundary used by repositories.
protocol APIClient: Sendable {
    func response(for request: URLRequest) async throws -> APIResponse
}

/// URLSession-backed production API client.
struct URLSessionAPIClient: APIClient {
    func response(for request: URLRequest) async throws -> APIResponse {
        let (data, response) = try await URLSession.shared.data(for: request)
        guard let httpResponse = response as? HTTPURLResponse else {
            throw APIClientError.invalidResponse
        }
        return APIResponse(data: data, statusCode: httpResponse.statusCode)
    }
}

/// Deterministic API client for previews and tests.
struct StubAPIClient: APIClient {
    let data: Data
    var statusCode: Int
    var error: APIClientError?

    init(data: Data, statusCode: Int = 200, error: APIClientError? = nil) {
        self.data = data
        self.statusCode = statusCode
        self.error = error
    }

    func response(for request: URLRequest) async throws -> APIResponse {
        if let error {
            throw error
        }
        return APIResponse(data: data, statusCode: statusCode)
    }
}

/// Errors owned by the shared transport boundary.
enum APIClientError: Error, Sendable {
    case invalidResponse
}
