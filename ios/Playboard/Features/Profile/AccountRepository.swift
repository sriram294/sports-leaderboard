import Foundation
#if canImport(FoundationNetworking)
import FoundationNetworking
#endif

/// Account-lifecycle failures with recovery guidance for destructive actions.
enum AccountRepositoryError: Error, Equatable, Sendable {
    case offline
    case confirmationRejected
    case unauthorized
    case invalidResponse

    var message: String {
        switch self {
        case .offline: "Playboard could not reach the server. Check your connection and try again."
        case .confirmationRejected: "Type DELETE exactly to confirm account deletion."
        case .unauthorized: "Your session expired. Sign in again before deleting your account."
        case .invalidResponse: "Account deletion could not be completed. Try again."
        }
    }
}

/// API boundary for irreversible account deletion.
protocol AccountRepository: Sendable {
    func deleteAccount() async throws
}

/// Deletes the authenticated account and leaves local cleanup to the session owner.
actor LiveAccountRepository: AccountRepository {
    private let apiClient: any APIClient
    private let baseURL: URL
    private let accessToken: String

    init(apiClient: any APIClient, baseURL: URL, accessToken: String) {
        self.apiClient = apiClient
        self.baseURL = baseURL
        self.accessToken = accessToken
    }

    func deleteAccount() async throws {
        var request = URLRequest(url: baseURL.appendingPathComponent("users/me"))
        request.httpMethod = "DELETE"
        request.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = Data("{\"confirmation\":\"DELETE\"}".utf8)

        do {
            let response = try await apiClient.response(for: request)
            guard (200..<300).contains(response.statusCode) else {
                if response.statusCode == 401 { throw AccountRepositoryError.unauthorized }
                if response.statusCode == 422 { throw AccountRepositoryError.confirmationRejected }
                throw AccountRepositoryError.invalidResponse
            }
        } catch let error as AccountRepositoryError {
            throw error
        } catch let error as URLError
            where error.code == .notConnectedToInternet || error.code == .networkConnectionLost {
            throw AccountRepositoryError.offline
        } catch {
            throw AccountRepositoryError.invalidResponse
        }
    }
}
