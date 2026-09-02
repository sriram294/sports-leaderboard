import Foundation
import Testing
@testable import Playboard

@Suite("Matches view model")
@MainActor
struct MatchesViewModelTests {
    @Test("Groups newest-first history, expands detail, and appends unique older matches")
    func browsesHistory() async throws {
        let repository = MatchesRepositoryFake()
        let viewModel = MatchesViewModel(repository: repository)
        viewModel.select(group: Self.group)
        try await waitUntil { viewModel.state.matches.count == 2 }

        #expect(viewModel.sections().count == 2)
        #expect(viewModel.sections().first?.matches.first?.id == "new")
        viewModel.toggleDetail(matchID: "new")
        try await waitUntil { viewModel.state.detail?.id == "new" }
        viewModel.loadMore()
        try await waitUntil { viewModel.state.matches.count == 3 }

        #expect(viewModel.state.matches.map(\.id) == ["new", "old", "older"])
        #expect(viewModel.state.nextCursor == nil)
    }

    @Test("Mine filter replaces the list from the first page")
    func filtersMine() async throws {
        let repository = MatchesRepositoryFake()
        let viewModel = MatchesViewModel(repository: repository)
        viewModel.select(group: Self.group)
        try await waitUntil { viewModel.state.matches.count == 2 }
        viewModel.toggleMineOnly()
        try await waitUntil { viewModel.state.matches.map(\.id) == ["mine"] }

        #expect(viewModel.state.mineOnly)
        #expect(await repository.mineValues.last == true)
    }

    private static let group = PlayGroup(
        id: "group", name: "Group", avatarColor: "#9ADE28", sportCode: "badminton_doubles",
        memberCount: 4, matchCount: 3, myRole: .member, sessionStart: nil, sessionEnd: nil
    )

    private func waitUntil(_ predicate: @escaping @MainActor () async -> Bool) async throws {
        for _ in 0..<100 {
            if await predicate() { return }
            try await Task.sleep(for: .milliseconds(5))
        }
        Issue.record("Timed out waiting for match state")
    }
}

private actor MatchesRepositoryFake: MatchRepository {
    private(set) var mineValues: [Bool] = []
    func matches(groupID: String, cursor: String?, mine: Bool) async throws -> MatchPage {
        mineValues.append(mine)
        if mine { return MatchPage(matches: [Self.summary("mine", day: 3)], nextCursor: nil) }
        if cursor != nil { return MatchPage(matches: [Self.summary("old", day: 1), Self.summary("older", day: 0)], nextCursor: nil) }
        return MatchPage(matches: [Self.summary("new", day: 3), Self.summary("old", day: 1)], nextCursor: "cursor")
    }
    func detail(groupID: String, matchID: String) async throws -> MatchDetail {
        MatchDetail(
            id: matchID, playedAt: Date(timeIntervalSince1970: 300), teams: [], sets: [],
            recordedBy: MatchActor(userID: "p1", displayName: "Player"),
            recordedAt: Date(timeIntervalSince1970: 300), events: []
        )
    }
    func record(groupID: String, request: RecordMatchRequest, requestID: String) async throws -> MatchDetail { fatalError("unused") }
    private static func summary(_ id: String, day: TimeInterval) -> MatchSummary {
        MatchSummary(id: id, playedAt: Date(timeIntervalSince1970: day * 86_400), teams: [], sets: [])
    }
}
