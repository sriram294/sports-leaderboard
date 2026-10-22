import Foundation
#if canImport(FoundationNetworking)
import FoundationNetworking
#endif
import Testing
@testable import Playboard

@Suite("Parity helpers")
struct ParityHelpersTests {
    @Test("API dates parse with and without fractional seconds")
    func parsesDates() throws {
        let whole = try #require(PlayboardJSON.parseDate("2026-09-13T11:46:40Z"))
        let fractional = try #require(PlayboardJSON.parseDate("2026-09-13T11:46:40.123456Z"))
        #expect(whole.timeIntervalSince1970 == 1_789_300_000)
        #expect(abs(fractional.timeIntervalSince1970 - 1_789_300_000.123456) < 0.001)
        #expect(PlayboardJSON.parseDate("not a date") == nil)
    }

    @Test("Attendance grid lays months out Monday-first in whole weeks")
    func attendanceGrid() throws {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "UTC")!
        let grid = AttendanceGrid(calendar: calendar)
        let months = grid.months(endingAt: Date(timeIntervalSince1970: 1_789_473_600)) // 2026-09-15
        #expect(months.count == 3)
        let september = try #require(months.last)
        #expect(calendar.component(.month, from: september) == 9)
        let cells = grid.cells(for: september)
        // 1 September 2026 is a Tuesday: one leading blank, 30 days, padded to 35.
        #expect(cells.count == 35)
        #expect(cells[0] == nil)
        #expect(cells[1] == september)
        let window = try #require(grid.window(for: months))
        #expect(calendar.component(.month, from: window.start) == 7)
        #expect(calendar.component(.month, from: window.end) == 10)
        #expect(grid.days(from: [september.addingTimeInterval(3_600), september.addingTimeInterval(7_200)]).count == 1)
    }

    @Test("Recent matches are framed from the viewed player's side")
    func recentMatchRow() throws {
        let match = MatchSummary(
            id: "m", playedAt: Date(timeIntervalSince1970: 0),
            teams: [
                MatchTeam(teamNo: 1, isWinner: true, players: [Self.player("a"), Self.player("b")]),
                MatchTeam(teamNo: 2, isWinner: false, players: [Self.player("c"), Self.player("d")])
            ],
            sets: [MatchSet(setNo: 2, team1Score: 21, team2Score: 19), MatchSet(setNo: 1, team1Score: 15, team2Score: 21)]
        )
        let row = try #require(RecentMatchRow(match: match, userID: "c"))
        #expect(!row.isWin)
        #expect(row.partnerNames == "d")
        #expect(row.opponentNames == "a & b")
        #expect(row.scores.map(\.mine) == [21, 19])
        #expect(RecentMatchRow(match: match, userID: "stranger") == nil)
    }

    @Test("Update prompt only shows for a newer build with a link")
    func updateComparison() {
        let update = AppUpdate(versionCode: 5, versionName: "1.1", downloadURL: URL(string: "https://apps.apple.com/app/id1"), available: true)
        #expect(update.isNewer(thanBuild: 4))
        #expect(!update.isNewer(thanBuild: 5))
        #expect(!AppUpdate(versionCode: 9, versionName: nil, downloadURL: nil, available: true).isNewer(thanBuild: 1))
    }

    @Test("Month keys format for labels")
    func monthLabels() {
        let trophy = MonthlyTrophy(month: "2026-08", userID: "u", displayName: "U", avatarColor: "#000000")
        #expect(trophy.shortMonthLabel == "Aug")
        #expect(trophy.longMonthLabel == "August 2026")
        #expect(PlayboardMonth.label("garbage", format: "MMM") == "garbage")
    }

    @Test("Reviewer login maps a 401 to invalid credentials")
    func reviewerLogin() async throws {
        let failing = LiveAuthRepository(
            apiClient: StubAPIClient(data: Data(##"{"code":"REVIEWER_CREDENTIALS_INVALID"}"##.utf8), statusCode: 401),
            sessionStore: InMemorySessionStore(), baseURL: URL(string: "https://example.test/api/v1"), clock: SystemPlayboardClock()
        )
        await #expect(throws: AuthError.invalidReviewerCredentials) {
            try await failing.signInAsReviewer(username: "review", password: "wrong")
        }

        let json = ##"{"accessToken":"a","refreshToken":"r","expiresIn":900,"user":{"id":"u","displayName":"Reviewer","email":"r@example.com","photoUrl":null,"avatarId":null,"avatarColor":"#9ADE28","authProviders":["reviewer"]}}"##
        let store = InMemorySessionStore()
        let working = LiveAuthRepository(
            apiClient: StubAPIClient(data: Data(json.utf8)), sessionStore: store,
            baseURL: URL(string: "https://example.test/api/v1"), clock: SystemPlayboardClock()
        )
        let session = try await working.signInAsReviewer(username: "review", password: "right")
        #expect(session.user.displayName == "Reviewer")
        let renamed = AuthenticatedUser(id: "u", displayName: "Renamed", email: "r@example.com", photoURL: nil,
                                        avatarID: nil, avatarColor: "#9ADE28", authProviders: [])
        await working.updateUser(renamed)
        #expect(await store.load()?.user.displayName == "Renamed")
    }

    @Test("Device registration posts the iOS platform")
    func registersDevice() async throws {
        let client = DeviceRecordingClient()
        let repository = LiveDeviceRepository(apiClient: client, baseURL: URL(string: "https://example.test/api/v1")!, accessToken: "a")
        try await repository.register(token: "fcm-token")
        try await repository.unregister(token: "fcm-token")
        let calls = await client.calls
        #expect(calls.map(\.method) == ["POST", "DELETE"])
        #expect(calls.first?.body.contains(#""platform":"ios""#) == true)
        #expect(calls.last?.body.contains(#""token":"fcm-token""#) == true)
    }

    private static func player(_ id: String) -> MatchPlayer {
        MatchPlayer(userID: id, displayName: id, avatarColor: "#9ADE28", photoURL: nil, avatarID: nil)
    }
}

private actor DeviceRecordingClient: APIClient {
    private(set) var calls: [(method: String, body: String)] = []
    func response(for request: URLRequest) async throws -> APIResponse {
        calls.append((request.httpMethod ?? "", request.httpBody.map { String(decoding: $0, as: UTF8.self) } ?? ""))
        return APIResponse(data: Data(), statusCode: 204)
    }
}
