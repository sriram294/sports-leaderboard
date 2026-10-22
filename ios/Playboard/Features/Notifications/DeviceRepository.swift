import Foundation
#if canImport(FoundationNetworking)
import FoundationNetworking
#endif

/// Registers this device's push token with the backend so it receives group notifications.
protocol DeviceRepository: Sendable {
    /// Idempotent upsert of the current FCM token.
    func register(token: String) async throws
    /// Removes the token on sign-out so the device stops receiving this account's pushes.
    func unregister(token: String) async throws
}

/// `POST /devices` / `DELETE /devices` implementation.
actor LiveDeviceRepository: DeviceRepository {
    private let api: AuthorizedAPI
    private let encoder = PlayboardJSON.encoder()

    init(apiClient: any APIClient, baseURL: URL, accessToken: String, refreshAccessToken: (@Sendable () async throws -> String)? = nil) {
        api = AuthorizedAPI(apiClient: apiClient, baseURL: baseURL, accessToken: accessToken, refreshAccessToken: refreshAccessToken)
    }

    func register(token: String) async throws {
        let body = try encoder.encode(RegisterBody(token: token, platform: "ios"))
        try check(try await api.send(path: "devices", method: "POST", body: body))
    }

    func unregister(token: String) async throws {
        let body = try encoder.encode(UnregisterBody(token: token))
        try check(try await api.send(path: "devices", method: "DELETE", body: body))
    }

    private func check(_ response: APIResponse) throws {
        guard (200..<300).contains(response.statusCode) else { throw GroupRepositoryError.invalidResponse }
    }
}

private struct RegisterBody: Encodable { let token: String; let platform: String }
private struct UnregisterBody: Encodable { let token: String }
