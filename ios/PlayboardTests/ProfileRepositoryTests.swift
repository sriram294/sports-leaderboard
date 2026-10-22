import Foundation
#if canImport(FoundationNetworking)
import FoundationNetworking
#endif
import Testing
@testable import Playboard

@Suite("Profile repository")
struct ProfileRepositoryTests {
    @Test("Decodes stats with trophies, finishes, and fractional match times")
    func decodesStats() async throws {
        let client = ProfileRecordingClient(responses: [APIResponse(data: Data(Self.statsJSON.utf8), statusCode: 200)])
        let stats = try await repository(client).stats(groupID: "g1", userID: "u1")

        #expect(stats.avatarID == "avatar3")
        #expect(stats.trophies.map(\.month) == ["2026-08"])
        #expect(stats.monthlyFinishes.map(\.rank) == [2, nil])
        #expect(stats.recentMatches.first?.id == "m1")
        #expect(await client.lastPath == "/api/v1/groups/g1/members/u1/stats")
    }

    @Test("A pre-trophy backend still decodes with empty lists")
    func decodesLegacyStats() async throws {
        let json = ##"{"userId":"u1","displayName":"Priya","avatarColor":"#9ADE28","matchesPlayed":0,"wins":0,"losses":0,"pointsFor":0,"pointsAgainst":0,"winRate":0}"##
        let client = ProfileRecordingClient(responses: [APIResponse(data: Data(json.utf8), statusCode: 200)])
        let stats = try await repository(client).stats(groupID: "g1", userID: "u1")
        #expect(stats.trophies.isEmpty)
        #expect(stats.recentMatches.isEmpty)
        #expect(stats.currentStreak == 0)
    }

    @Test("Attendance sends the window and parses instants")
    func loadsAttendance() async throws {
        let json = ##"{"playedAt":["2026-09-04T13:30:00Z","2026-09-04T14:00:00.250Z"]}"##
        let client = ProfileRecordingClient(responses: [APIResponse(data: Data(json.utf8), statusCode: 200)])
        let window = DateInterval(start: Date(timeIntervalSince1970: 1_786_000_000), duration: 86_400)
        let instants = try await repository(client).attendance(groupID: "g1", userID: "u1", window: window)

        #expect(instants.count == 2)
        #expect(await client.lastQuery?.contains("from=") == true)
        #expect(await client.lastQuery?.contains("to=") == true)
    }

    @Test("Trophies drop blank records and pass the limit")
    func loadsTrophies() async throws {
        let json = ##"[{"month":"2026-09","userId":"u1","displayName":"Priya","avatarColor":"#9ADE28"},{"month":""}]"##
        let client = ProfileRecordingClient(responses: [APIResponse(data: Data(json.utf8), statusCode: 200)])
        let trophies = try await repository(client).trophies(groupID: "g1", limit: 6)
        #expect(trophies.map(\.userID) == ["u1"])
        #expect(await client.lastQuery == "limit=6")
    }

    @Test("Avatar selection patches the avatar endpoint")
    func selectsAvatar() async throws {
        let client = ProfileRecordingClient(responses: [APIResponse(data: Data(Self.userJSON.utf8), statusCode: 200)])
        let user = try await repository(client).updateAvatar("avatar7")
        #expect(user.avatarID == "avatar7")
        #expect(await client.lastMethod == "PATCH")
        #expect(await client.lastPath == "/api/v1/users/me/avatar")
        #expect(await client.lastBody?.contains(#""avatarId":"avatar7""#) == true)
    }

    @Test("Photo upload is multipart with a file field")
    func uploadsPhoto() async throws {
        let client = ProfileRecordingClient(responses: [APIResponse(data: Data(Self.userJSON.utf8), statusCode: 200)])
        _ = try await repository(client).uploadPhoto(Data([0xFF, 0xD8]), mimeType: "image/jpeg")
        #expect(await client.lastContentType?.hasPrefix("multipart/form-data; boundary=") == true)
        #expect(await client.lastBody?.contains(#"name="file"; filename="avatar.jpg""#) == true)
    }

    @Test("Refreshes once on 401 and maps transport failures")
    func refreshesAndMapsOffline() async throws {
        let client = ProfileRecordingClient(responses: [
            APIResponse(data: Data(), statusCode: 401),
            APIResponse(data: Data("[]".utf8), statusCode: 200)
        ])
        let repo = LiveProfileRepository(
            apiClient: client, baseURL: URL(string: "https://example.test/api/v1")!, accessToken: "old",
            refreshAccessToken: { "new" }
        )
        _ = try await repo.partners(groupID: "g1", userID: "u1", window: nil)
        #expect(await client.lastAuthorization == "Bearer new")

        let offline = repository(ProfileRecordingClient(responses: []))
        await #expect(throws: AuthorizedAPIError.offline) { try await offline.trophies(groupID: "g1", limit: 6) }
        #expect(profileErrorMessage(AuthorizedAPIError.offline) == GroupRepositoryError.offline.message)
    }

    private func repository(_ client: ProfileRecordingClient) -> LiveProfileRepository {
        LiveProfileRepository(apiClient: client, baseURL: URL(string: "https://example.test/api/v1")!, accessToken: "access")
    }

    private static let userJSON = ##"{"id":"u1","displayName":"Priya","email":"p@example.com","photoUrl":null,"avatarId":"avatar7","avatarColor":"#9ADE28","authProviders":["google"]}"##

    private static let statsJSON = """
    {"userId":"u1","displayName":"Priya","photoUrl":null,"avatarId":"avatar3","avatarColor":"#9ADE28",
     "matchesPlayed":4,"wins":3,"losses":1,"pointsFor":84,"pointsAgainst":70,"winRate":0.75,
     "currentStreak":2,"bestStreak":3,
     "recentMatches":[{"id":"m1","playedAt":"2026-09-04T13:30:00.123456Z","teams":[],"sets":[]}],
     "trophies":[{"month":"2026-08","userId":"u1","displayName":"Priya","avatarColor":"#9ADE28","rating":46.2}],
     "monthlyFinishes":[{"month":"2026-07","rank":2,"qualifiedPlayers":5},{"month":"2026-08","rank":null,"qualifiedPlayers":6}]}
    """
}

private actor ProfileRecordingClient: APIClient {
    private var responses: [APIResponse]
    private(set) var requests: [URLRequest] = []

    init(responses: [APIResponse]) { self.responses = responses }

    var lastPath: String? { requests.last?.url?.path }
    var lastQuery: String? { requests.last?.url?.query }
    var lastMethod: String? { requests.last?.httpMethod }
    var lastAuthorization: String? { requests.last?.value(forHTTPHeaderField: "Authorization") }
    var lastContentType: String? { requests.last?.value(forHTTPHeaderField: "Content-Type") }
    var lastBody: String? { requests.last?.httpBody.flatMap { String(decoding: $0, as: UTF8.self) } }

    func response(for request: URLRequest) async throws -> APIResponse {
        requests.append(request)
        guard !responses.isEmpty else { throw APIClientError.invalidResponse }
        return responses.removeFirst()
    }
}
