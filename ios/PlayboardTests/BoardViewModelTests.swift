import Foundation
import Testing
@testable import Playboard

@MainActor
@Suite("Board view model")
struct BoardViewModelTests {
    @Test("Metric sorting keeps provisional players last and canonical ties stable")
    func sortsStablePartitions() {
        var state = BoardUiState(rankings: [
            entry(rank: 1, id: "a", games: 5, provisional: false),
            entry(rank: 2, id: "b", games: 5, provisional: false),
            entry(rank: 3, id: "c", games: 20, provisional: true)
        ])
        state.metric = .games

        #expect(state.tableRows.map(\.userID) == ["a", "b", "c"])
        #expect(state.podium.map(\.userID) == ["a", "b"])
    }

    @Test("This Month uses injected local boundaries and All Time removes them")
    func changesRangeWindow() async throws {
        let repository = BoardRepositoryFake()
        let now = Date(timeIntervalSince1970: 1_788_102_000)
        let expected = DateInterval(start: now, duration: 100)
        let viewModel = BoardViewModel(repository: repository, clock: BoardFixedClock(now: now), calendar: BoardFixedCalendar(interval: expected))

        viewModel.select(group: group(id: "g1"))
        try await waitUntil { await repository.windows.count == 1 }
        viewModel.setRange(.allTime)
        try await waitUntil { await repository.windows.count == 2 }

        #expect(await repository.windows[0] == expected)
        #expect(await repository.windows[1] == nil)
        #expect(viewModel.state.range == .allTime)
        #expect(viewModel.state.monthName == "August")
    }

    @Test("Changing groups cancels stale work")
    func cancelsStaleGroup() async throws {
        let repository = BoardRepositoryFake(firstDelay: .milliseconds(100))
        let viewModel = BoardViewModel(repository: repository, clock: BoardFixedClock(now: .now), calendar: BoardFixedCalendar(interval: nil))

        viewModel.select(group: group(id: "slow"))
        viewModel.select(group: group(id: "fast"))
        try await waitUntil { viewModel.state.rankings.first?.userID == "fast" }

        #expect(viewModel.state.groupID == "fast")
        #expect(viewModel.state.rankings.first?.userID == "fast")
    }

    private func group(id: String) -> PlayGroup {
        PlayGroup(id: id, name: id, avatarColor: "#9ADE28", sportCode: "badminton_doubles", memberCount: 3, matchCount: 2, myRole: .member, sessionStart: nil, sessionEnd: nil)
    }

    private func entry(rank: Int, id: String, games: Int, provisional: Bool) -> LeaderboardEntry {
        LeaderboardEntry(rank: rank, userID: id, displayName: id, photoURL: nil, avatarID: nil, avatarColor: "#9ADE28", gamesPlayed: games, wins: 2, losses: 1, pointsFor: 42, pointsAgainst: 30, winRate: 0.66, currentStreak: 1, bestStreak: 2, rating: 30, provisional: provisional, recentForm: [true, false])
    }

    private func waitUntil(_ predicate: @escaping @MainActor () async -> Bool) async throws {
        for _ in 0..<100 {
            if await predicate() { return }
            try await Task.sleep(for: .milliseconds(5))
        }
        Issue.record("Timed out waiting for asynchronous board state")
    }
}

private struct BoardFixedClock: PlayboardClock { let now: Date }

private struct BoardFixedCalendar: LeaderboardCalendaring {
    let interval: DateInterval?
    func monthInterval(containing date: Date) -> DateInterval? { interval }
    func monthName(containing date: Date) -> String { "August" }
}

private actor BoardRepositoryFake: LeaderboardRepository {
    private let firstDelay: Duration?
    private(set) var windows: [DateInterval?] = []
    private var calls = 0
    init(firstDelay: Duration? = nil) { self.firstDelay = firstDelay }

    func leaderboard(groupID: String, window: DateInterval?) async throws -> Leaderboard {
        calls += 1
        windows.append(window)
        if calls == 1, let firstDelay { try await Task.sleep(for: firstDelay) }
        let row = LeaderboardEntry(rank: 1, userID: groupID, displayName: groupID, photoURL: nil, avatarID: nil, avatarColor: "#9ADE28", gamesPlayed: 3, wins: 2, losses: 1, pointsFor: 42, pointsAgainst: 30, winRate: 0.66, currentStreak: 1, bestStreak: 2, rating: 30, provisional: false, recentForm: [true])
        return Leaderboard(rankings: [row], minGamesToRank: 2)
    }
}
