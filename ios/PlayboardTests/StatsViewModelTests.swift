import Foundation
import Testing
@testable import Playboard

@Suite("Stats view model")
@MainActor
struct StatsViewModelTests {
    @Test("Monthly insights use the month window for records and the biggest win")
    func loadsMonthlyInsights() async throws {
        let leaderboard = StatsLeaderboardFake()
        let viewModel = makeViewModel(leaderboard: leaderboard)
        viewModel.select(group: Self.group)
        try await waitUntil { viewModel.state.records != nil }

        let records = try #require(viewModel.state.records)
        #expect(records.winLeader?.userID == "u1") // server order, first with enough games
        #expect(records.mostActive?.userID == "u1")
        #expect(records.longestStreak?.userID == "u1")
        #expect(records.currentStreak == nil)
        #expect(records.totalMatches == 2)
        // The out-of-window blowout is ignored; the in-window 21–10 is the biggest win.
        #expect(viewModel.state.biggestWin?.match.id == "inside")
        #expect(viewModel.state.monthlyWinners.map(\.month) == ["2026-08"])
        #expect(await leaderboard.windows.first! != nil)
    }

    @Test("All time drops the window and uses the group match count")
    func loadsAllTime() async throws {
        let leaderboard = StatsLeaderboardFake()
        let viewModel = makeViewModel(leaderboard: leaderboard)
        viewModel.select(group: Self.group)
        try await waitUntil { viewModel.state.records != nil }
        viewModel.setRange(.allTime)
        try await waitUntil { await leaderboard.windows.count == 2 && viewModel.state.records?.totalMatches == 9 }
        #expect(await leaderboard.windows.last! == nil)
        #expect(viewModel.state.biggestWin?.match.id == "outside")
    }

    @Test("Partners default to the signed-in player and follow the picker")
    func loadsPartners() async throws {
        let profile = StatsProfileFake()
        let viewModel = makeViewModel(profile: profile)
        viewModel.select(group: Self.group)
        try await waitUntil { viewModel.state.records != nil }
        viewModel.togglePartners()
        try await waitUntil { !viewModel.state.partners.isEmpty }
        #expect(viewModel.state.selectedPlayerID == "u1")
        viewModel.selectPlayer("u2")
        try await waitUntil { await profile.partnerRequests.last == "u2" && !viewModel.state.isLoadingPartners }
        #expect(viewModel.state.partners.first?.userID == "partner-of-u2")
    }

    @Test("Records need a real streak and enough games")
    func recordRules() {
        let rows = [Self.entry("solo", games: 1, winRate: 1, best: 1, current: 1), Self.entry("steady", games: 5, winRate: 0.6, best: 1, current: 0)]
        let records = StatsRecords(rankings: rows, matchCount: 3)
        #expect(records.winLeader?.userID == "steady")
        #expect(records.longestStreak == nil)
        #expect(StatsRecords(rankings: [], matchCount: 0).winLeader == nil)
        #expect(BiggestWin(matches: []) == nil)
    }

    private func makeViewModel(
        leaderboard: StatsLeaderboardFake = StatsLeaderboardFake(),
        profile: StatsProfileFake = StatsProfileFake()
    ) -> StatsViewModel {
        StatsViewModel(
            leaderboardRepository: leaderboard,
            matchRepository: StatsMatchFake(),
            profileRepository: profile,
            currentUserID: "u1",
            clock: SeptemberClock(),
            calendar: SeptemberCalendar()
        )
    }

    private func waitUntil(_ predicate: @escaping @MainActor () async -> Bool) async throws {
        for _ in 0..<100 {
            if await predicate() { return }
            try await Task.sleep(for: .milliseconds(5))
        }
        Issue.record("Timed out waiting for stats state")
    }

    nonisolated static let group = PlayGroup(id: "g1", name: "Smashers", avatarColor: "#9ADE28", sportCode: "badminton_doubles",
                                 memberCount: 4, matchCount: 9, myRole: .member, sessionStart: nil, sessionEnd: nil)

    nonisolated static func entry(_ id: String, games: Int, winRate: Double, best: Int, current: Int, points: Int = 100) -> LeaderboardEntry {
        LeaderboardEntry(rank: 1, userID: id, displayName: id, photoURL: nil, avatarID: nil, avatarColor: "#9ADE28",
                         gamesPlayed: games, wins: Int(Double(games) * winRate), losses: games - Int(Double(games) * winRate),
                         pointsFor: points, pointsAgainst: 90, winRate: winRate, currentStreak: current, bestStreak: best, rating: nil)
    }
}

/// September 2026 in UTC.
private struct SeptemberCalendar: LeaderboardCalendaring {
    func monthInterval(containing date: Date) -> DateInterval? {
        DateInterval(start: Date(timeIntervalSince1970: 1_788_220_800), end: Date(timeIntervalSince1970: 1_790_812_800))
    }
    func monthName(containing date: Date) -> String { "September" }
}

private actor StatsLeaderboardFake: LeaderboardRepository {
    private(set) var windows: [DateInterval?] = []
    func leaderboard(groupID: String, window: DateInterval?) async throws -> Leaderboard {
        windows.append(window)
        return Leaderboard(rankings: [
            StatsViewModelTests.entry("u1", games: 6, winRate: 0.5, best: 3, current: 1, points: 140),
            StatsViewModelTests.entry("u2", games: 2, winRate: 1, best: 2, current: 1, points: 42)
        ], minGamesToRank: 1)
    }
}

private actor StatsMatchFake: MatchRepository {
    func matches(groupID: String, cursor: String?, mine: Bool) async throws -> MatchPage {
        MatchPage(matches: [
            Self.match("inside", at: 1_789_300_000, 21, 10),
            Self.match("outside", at: 1_780_000_000, 21, 2)
        ], nextCursor: nil)
    }
    func detail(groupID: String, matchID: String) async throws -> MatchDetail { throw MatchRepositoryError.invalidResponse }
    func record(groupID: String, request: RecordMatchRequest, requestID: String) async throws -> MatchDetail {
        throw MatchRepositoryError.invalidResponse
    }
    private static func match(_ id: String, at time: TimeInterval, _ team1: Int, _ team2: Int) -> MatchSummary {
        MatchSummary(id: id, playedAt: Date(timeIntervalSince1970: time), teams: [],
                     sets: [MatchSet(setNo: 1, team1Score: team1, team2Score: team2)])
    }
}

private actor StatsProfileFake: ProfileRepository {
    private(set) var partnerRequests: [String] = []
    func stats(groupID: String, userID: String) async throws -> PlayerStats { throw AuthorizedAPIError.offline }
    func partners(groupID: String, userID: String, window: DateInterval?) async throws -> [PartnerStats] {
        partnerRequests.append(userID)
        return [PartnerStats(userID: "partner-of-\(userID)", displayName: "Partner", avatarColor: "#9ADE28",
                             gamesTogether: 2, winsTogether: 1, winRate: 0.5)]
    }
    func attendance(groupID: String, userID: String, window: DateInterval) async throws -> [Date] { [] }
    func trophies(groupID: String, limit: Int) async throws -> [MonthlyTrophy] {
        [MonthlyTrophy(month: "2026-08", userID: "u1", displayName: "u1", avatarColor: "#9ADE28")]
    }
    func updateDisplayName(_ name: String) async throws -> AuthenticatedUser { throw AuthorizedAPIError.offline }
    func updateAvatar(_ avatarID: String) async throws -> AuthenticatedUser { throw AuthorizedAPIError.offline }
    func uploadPhoto(_ data: Data, mimeType: String) async throws -> AuthenticatedUser { throw AuthorizedAPIError.offline }
}
