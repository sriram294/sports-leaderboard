import Foundation
import Testing
@testable import Playboard

@Suite("Account repository")
struct AccountRepositoryTests {
    @Test("Deletion sends the exact confirmation and bearer token")
    func sendsDeletionRequest() async throws {
        let client = AccountRecordingAPIClient(response: APIResponse(data: Data(), statusCode: 204))
        let repository = LiveAccountRepository(
            apiClient: client,
            baseURL: URL(string: "https://example.test/api/v1")!,
            accessToken: "access-token"
        )

        try await repository.deleteAccount()

        let request = try #require(await client.request)
        #expect(request.httpMethod == "DELETE")
        #expect(request.url?.path == "/api/v1/users/me")
        #expect(request.value(forHTTPHeaderField: "Authorization") == "Bearer access-token")
        #expect(String(data: request.httpBody ?? Data(), encoding: .utf8) == "{\"confirmation\":\"DELETE\"}")
    }

    @Test("Unauthorized deletion remains retryable")
    func mapsUnauthorized() async {
        let client = AccountRecordingAPIClient(response: APIResponse(data: Data(), statusCode: 401))
        let repository = LiveAccountRepository(
            apiClient: client,
            baseURL: URL(string: "https://example.test/api/v1")!,
            accessToken: "expired"
        )

        await #expect(throws: AccountRepositoryError.unauthorized) {
            try await repository.deleteAccount()
        }
    }
}

private actor AccountRecordingAPIClient: APIClient {
    let responseValue: APIResponse
    private(set) var request: URLRequest?

    init(response: APIResponse) {
        responseValue = response
    }

    func response(for request: URLRequest) async throws -> APIResponse {
        self.request = request
        return responseValue
    }
}
