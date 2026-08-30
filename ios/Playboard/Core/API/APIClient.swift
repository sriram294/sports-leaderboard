import Foundation
#if canImport(FoundationNetworking)
import FoundationNetworking
#endif

/// Network boundary used by repositories in later slices.
protocol APIClient: Sendable {
    func data(for request: URLRequest) async throws -> Data
}

/// URLSession-backed production API client. No endpoint or credential is configured in S00.
struct URLSessionAPIClient: APIClient {
    func data(for request: URLRequest) async throws -> Data {
        let (data, response) = try await URLSession.shared.data(for: request)
        guard let httpResponse = response as? HTTPURLResponse,
              (200..<300).contains(httpResponse.statusCode) else {
            throw APIClientError.invalidResponse
        }
        return data
    }
}

/// Deterministic API client for previews and tests.
struct StubAPIClient: APIClient {
    let data: Data
    var error: APIClientError?

    init(data: Data, error: APIClientError? = nil) {
        self.data = data
        self.error = error
    }

    func data(for request: URLRequest) async throws -> Data {
        if let error {
            throw error
        }
        return data
    }
}

/// Errors owned by the shared transport boundary.
enum APIClientError: Error, Sendable {
    case invalidResponse
}
