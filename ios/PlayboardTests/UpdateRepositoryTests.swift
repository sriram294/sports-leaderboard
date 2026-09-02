import Foundation
#if canImport(FoundationNetworking)
import FoundationNetworking
#endif
import Testing
@testable import Playboard

@Suite("App update repository")
struct UpdateRepositoryTests {
    @Test("Requests iOS metadata from the public endpoint")
    func requestsIosMetadata() async throws {
        let client = UpdateRecordingClient(response: APIResponse(
            data: Data(#"{"versionCode":8,"versionName":"2.0","downloadUrl":"https://example.com/Playboard.ipa","available":true}"#.utf8),
            statusCode: 200
        ))
        let repository = LiveUpdateRepository(
            apiClient: client,
            baseURL: URL(string: "https://example.test/api/v1")!
        )

        let update = try await repository.latest()

        #expect(update.versionCode == 8)
        #expect(update.versionName == "2.0")
        #expect(update.available)
        #expect(await client.lastRequest?.httpMethod == "GET")
        #expect(await client.lastRequest?.url?.query == "platform=ios")
    }

    @Test("Preserves an unavailable response")
    func preservesUnavailableResponse() async throws {
        let client = UpdateRecordingClient(response: APIResponse(
            data: Data(#"{"versionCode":null,"versionName":null,"downloadUrl":null,"available":false}"#.utf8),
            statusCode: 200
        ))
        let repository = LiveUpdateRepository(
            apiClient: client,
            baseURL: URL(string: "https://example.test/api/v1")!
        )

        let update = try await repository.latest()

        #expect(!update.available)
        #expect(update.downloadURL == nil)
    }
}

private actor UpdateRecordingClient: APIClient {
    let responseValue: APIResponse
    private(set) var lastRequest: URLRequest?

    init(response: APIResponse) { responseValue = response }

    func response(for request: URLRequest) async throws -> APIResponse {
        lastRequest = request
        return responseValue
    }
}
