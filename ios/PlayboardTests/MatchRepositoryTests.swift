import Foundation
#if canImport(FoundationNetworking)
import FoundationNetworking
#endif
import Testing
@testable import Playboard

@Suite("Match repository")
struct MatchRepositoryTests {
    @Test("Loads a page with cursor, mine filter, and fractional timestamps")
    func loadsMatchPage() async throws {
        let client = MatchRecordingAPIClient(responses: [APIResponse(data: Self.pageJSON, statusCode: 200)])
        let repository = LiveMatchRepository(
            apiClient: client,
            baseURL: URL(string: "https://example.test/api/v1")!,
            accessToken: "access"
        )

        let page = try await repository.matches(groupID: "group-1", cursor: "next value", mine: true)

        #expect(page.matches.first?.id == "match-1")
        #expect(page.matches.first?.playedAt.timeIntervalSince1970 == 1_786_258_680.123)
        #expect(await client.lastQuery?.contains("mine=true") == true)
        #expect(await client.lastQuery?.contains("cursor=next%20value") == true)
    }

    @Test("Records with a stable idempotency key and encoded existing DTO")
    func recordsWithIdempotencyKey() async throws {
        let client = MatchRecordingAPIClient(responses: [APIResponse(data: Self.detailJSON, statusCode: 201)])
        let repository = LiveMatchRepository(
            apiClient: client,
            baseURL: URL(string: "https://example.test/api/v1")!,
            accessToken: "access"
        )
        let request = RecordMatchRequest(
            playedAt: Date(timeIntervalSince1970: 1_786_262_280),
            teams: [.init(teamNo: 1, playerIds: ["p1", "p2"]), .init(teamNo: 2, playerIds: ["p3", "p4"])],
            sets: [.init(setNo: 1, team1Score: 21, team2Score: 12)],
            winningTeamNo: 1
        )

        _ = try await repository.record(groupID: "group-1", request: request, requestID: "attempt-42")

        #expect(await client.lastIdempotencyKey == "attempt-42")
        #expect(await client.lastMethod == "POST")
        #expect(await client.lastBodyString?.contains(#"\"winningTeamNo\":1"#) == true)
    }

    @Test("Maps backend validation problem codes")
    func mapsValidation() async {
        let problem = APIResponse(data: Data(#"{"code":"MATCH_INVALID_SCORES"}"#.utf8), statusCode: 422)
        let repository = LiveMatchRepository(
            apiClient: MatchRecordingAPIClient(responses: [problem]),
            baseURL: URL(string: "https://example.test/api/v1")!,
            accessToken: "access"
        )

        await #expect(throws: MatchRepositoryError.invalidScores) {
            _ = try await repository.matches(groupID: "group-1", cursor: nil, mine: false)
        }
    }

    @Test("Maps generic forbidden responses")
    func mapsForbidden() async {
        let repository = LiveMatchRepository(
            apiClient: MatchRecordingAPIClient(responses: [APIResponse(data: Data(), statusCode: 403)]),
            baseURL: URL(string: "https://example.test/api/v1")!,
            accessToken: "access"
        )

        await #expect(throws: MatchRepositoryError.forbidden) {
            _ = try await repository.matches(groupID: "group-1", cursor: nil, mine: false)
        }
    }

    private static let pageJSON = Data(#"{"matches":[{"id":"match-1","playedAt":"2026-08-09T06:58:00.123Z","teams":[{"teamNo":1,"isWinner":true,"players":[]},{"teamNo":2,"isWinner":false,"players":[]}],"sets":[{"setNo":1,"team1Score":21,"team2Score":12}]}],"nextCursor":null}"#.utf8)
    private static let detailJSON = Data(#"{"id":"match-1","playedAt":"2026-08-09T06:58:00Z","teams":[{"teamNo":1,"isWinner":true,"players":[]},{"teamNo":2,"isWinner":false,"players":[]}],"sets":[{"setNo":1,"team1Score":21,"team2Score":12}],"recordedBy":{"userId":"p1","displayName":"Priya"},"recordedAt":"2026-08-09T06:58:00Z","events":[]}"#.utf8)
}

private actor MatchRecordingAPIClient: APIClient {
    private var responses: [APIResponse]
    private(set) var requests: [URLRequest] = []
    init(responses: [APIResponse]) { self.responses = responses }
    var lastQuery: String? { requests.last?.url?.query }
    var lastIdempotencyKey: String? { requests.last?.value(forHTTPHeaderField: "Idempotency-Key") }
    var lastMethod: String? { requests.last?.httpMethod }
    var lastBodyString: String? { requests.last?.httpBody.flatMap { String(data: $0, encoding: .utf8) } }
    func response(for request: URLRequest) async throws -> APIResponse {
        requests.append(request)
        guard !responses.isEmpty else { throw APIClientError.invalidResponse }
        return responses.removeFirst()
    }
}
