import Foundation
#if canImport(FoundationNetworking)
import FoundationNetworking
#endif
import Testing
@testable import Playboard

@Suite("Group repository")
struct GroupRepositoryTests {
    @Test("Loads the existing group contract with bearer authorization")
    func loadsGroups() async throws {
        let client = GroupRecordingAPIClient(responses: [groupResponse])
        let store = KeyValueSelectedGroupStore(store: InMemoryKeyValueStore())
        let repository = LiveGroupRepository(apiClient: client, selectedGroupStore: store, baseURL: URL(string: "https://example.test/api/v1")!, accessToken: "access-token")

        let groups = try await repository.loadGroups()

        #expect(groups.first?.name == "Saturday Smashers")
        #expect(groups.first?.myRole == .owner)
        #expect(await client.lastAuthorization == "Bearer access-token")
        #expect(await client.lastPath == "/api/v1/groups")
    }

    @Test("Persists selected group independently of the repository")
    func persistsSelection() async {
        let values = InMemoryKeyValueStore()
        let store = KeyValueSelectedGroupStore(store: values)
        let repository = LiveGroupRepository(apiClient: StubAPIClient(data: Data()), selectedGroupStore: store, baseURL: URL(string: "https://example.test/api/v1")!, accessToken: "token")

        await repository.selectGroup("group-2")

        #expect(await repository.selectedGroupID() == "group-2")
    }

    @Test("Maps stable invalid-invite problem codes")
    func mapsInvalidInvite() async {
        let client = GroupRecordingAPIClient(responses: [APIResponse(data: Data(#"{"status":404,"code":"GROUP_INVITE_INVALID"}"#.utf8), statusCode: 404)])
        let repository = LiveGroupRepository(apiClient: client, selectedGroupStore: KeyValueSelectedGroupStore(store: InMemoryKeyValueStore()), baseURL: URL(string: "https://example.test/api/v1")!, accessToken: "token")

        await #expect(throws: GroupRepositoryError.invalidInvite) { try await repository.joinGroup(code: "bad") }
    }

    @Test("Refreshes once and retries an unauthorized group request")
    func refreshesUnauthorizedRequest() async throws {
        let client = GroupRecordingAPIClient(responses: [APIResponse(data: Data(), statusCode: 401), groupResponse])
        let repository = LiveGroupRepository(
            apiClient: client,
            selectedGroupStore: KeyValueSelectedGroupStore(store: InMemoryKeyValueStore()),
            baseURL: URL(string: "https://example.test/api/v1")!,
            accessToken: "expired",
            refreshAccessToken: { "rotated" }
        )

        _ = try await repository.loadGroups()

        #expect(await client.authorizations == ["Bearer expired", "Bearer rotated"])
    }

    private var groupResponse: APIResponse {
        APIResponse(data: Data(##"{"groups":[{"id":"group-1","name":"Saturday Smashers","avatarColor":"#9ADE28","sportCode":"badminton_doubles","memberCount":6,"matchCount":10,"myRole":"owner","sessionStart":"19:00","sessionEnd":"21:00"}]}"##.utf8), statusCode: 200)
    }
}

private actor GroupRecordingAPIClient: APIClient {
    private var responses: [APIResponse]
    private(set) var requests: [URLRequest] = []
    init(responses: [APIResponse]) { self.responses = responses }
    var lastAuthorization: String? { requests.last?.value(forHTTPHeaderField: "Authorization") }
    var lastPath: String? { requests.last?.url?.path }
    var authorizations: [String?] { requests.map { $0.value(forHTTPHeaderField: "Authorization") } }
    func response(for request: URLRequest) async throws -> APIResponse {
        requests.append(request)
        guard !responses.isEmpty else { throw APIClientError.invalidResponse }
        return responses.removeFirst()
    }
}
