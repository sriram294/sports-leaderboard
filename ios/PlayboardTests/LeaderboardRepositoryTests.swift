import Foundation
#if canImport(FoundationNetworking)
import FoundationNetworking
#endif
import Testing
@testable import Playboard

@Suite("Leaderboard repository")
struct LeaderboardRepositoryTests {
    @Test("Maps rankings and sends a half-open month window")
    func mapsWindowedLeaderboard() async throws {
        let client = LeaderboardRecordingClient(responses: [response])
        let repository = LiveLeaderboardRepository(apiClient: client, baseURL: URL(string: "https://example.test/api/v1")!, accessToken: "token")
        let window = DateInterval(start: Date(timeIntervalSince1970: 1_788_102_000), end: Date(timeIntervalSince1970: 1_790_780_400))

        let leaderboard = try await repository.leaderboard(groupID: "group-1", window: window)

        #expect(leaderboard.rankings.first?.pointsDifference == 72)
        #expect(leaderboard.rankings.first?.recentForm == [true, false, true])
        #expect(await client.lastAuthorization == "Bearer token")
        #expect(await client.lastQuery?.contains("from=") == true)
        #expect(await client.lastQuery?.contains("to=") == true)
    }

    @Test("All-time requests omit calendar query parameters")
    func omitsAllTimeWindow() async throws {
        let client = LeaderboardRecordingClient(responses: [response])
        let repository = LiveLeaderboardRepository(apiClient: client, baseURL: URL(string: "https://example.test/api/v1")!, accessToken: "token")

        _ = try await repository.leaderboard(groupID: "group-1", window: nil)

        #expect(await client.lastQuery == nil)
    }

    private var response: APIResponse {
        APIResponse(data: Data(##"{"rankings":[{"rank":1,"userId":"p1","displayName":"Priya","photoUrl":null,"avatarId":"avatar2","avatarColor":"#FF3D8A","gamesPlayed":6,"wins":5,"losses":1,"pointsFor":252,"pointsAgainst":180,"winRate":0.833,"currentStreak":3,"bestStreak":4,"rating":54.1,"provisional":false,"recentForm":[true,false,true]}],"minGamesToRank":3}"##.utf8), statusCode: 200)
    }
}

private actor LeaderboardRecordingClient: APIClient {
    private var responses: [APIResponse]
    private(set) var requests: [URLRequest] = []
    init(responses: [APIResponse]) { self.responses = responses }
    var lastAuthorization: String? { requests.last?.value(forHTTPHeaderField: "Authorization") }
    var lastQuery: String? { requests.last?.url?.query }
    func response(for request: URLRequest) async throws -> APIResponse {
        requests.append(request)
        guard !responses.isEmpty else { throw APIClientError.invalidResponse }
        return responses.removeFirst()
    }
}
