import Foundation
#if canImport(FoundationNetworking)
import FoundationNetworking
#endif

/// Metadata returned by the public platform-specific update endpoint.
struct AppUpdate: Codable, Equatable, Sendable {
    let versionCode: Int?
    let versionName: String?
    let downloadURL: URL?
    let available: Bool

    enum CodingKeys: String, CodingKey {
        case versionCode
        case versionName
        case downloadURL = "downloadUrl"
        case available
    }
}

/// Stable failures for the non-authenticated update check.
enum UpdateRepositoryError: Error, Equatable, Sendable {
    case offline
    case invalidResponse
}

/// Data boundary for the public iOS update metadata contract.
protocol UpdateRepository: Sendable {
    func latest() async throws -> AppUpdate
}

/// Reads the latest iOS release metadata without requiring a signed-in session.
actor LiveUpdateRepository: UpdateRepository {
    private let apiClient: any APIClient
    private let baseURL: URL
    private let decoder = JSONDecoder()

    init(apiClient: any APIClient, baseURL: URL) {
        self.apiClient = apiClient
        self.baseURL = baseURL
    }

    func latest() async throws -> AppUpdate {
        var components = URLComponents(
            url: baseURL.appendingPathComponent("app/update"),
            resolvingAgainstBaseURL: false
        )
        components?.queryItems = [URLQueryItem(name: "platform", value: "ios")]
        guard let url = components?.url else { throw UpdateRepositoryError.invalidResponse }

        var request = URLRequest(url: url)
        request.httpMethod = "GET"
        do {
            let response = try await apiClient.response(for: request)
            guard (200..<300).contains(response.statusCode) else {
                throw UpdateRepositoryError.invalidResponse
            }
            do {
                return try decoder.decode(AppUpdate.self, from: response.data)
            } catch {
                throw UpdateRepositoryError.invalidResponse
            }
        } catch let error as UpdateRepositoryError {
            throw error
        } catch is CancellationError {
            throw CancellationError()
        } catch {
            throw UpdateRepositoryError.offline
        }
    }
}
