import Foundation
import Testing
@testable import Playboard

@Suite("Add match state and view model")
@MainActor
struct AddMatchViewModelTests {
    @Test("Requires four distinct players, non-tied sets, and the derived winner")
    func validatesForm() {
        var state = AddMatchUiState(
            team1: ["p1", "p2"],
            team2: ["p3", "p4"],
            sets: [MatchSetDraft(team1: "21", team2: "18")],
            selectedWinner: 2
        )

        #expect(state.validationMessage == "Confirm the team that won the most sets.")
        state.selectedWinner = 1
        #expect(state.validationMessage == nil)
        #expect(state.canSubmit)
        state.sets[0].team2 = "21"
        #expect(!state.canSubmit)
    }

    @Test("Offline retry reuses the exact request and key while duplicate taps are ignored")
    func retryIsDuplicateSafe() async throws {
        let matches = AddMatchRepositoryFake()
        let groups = AddMatchGroupRepositoryFake()
        let viewModel = AddMatchViewModel(
            matchRepository: matches,
            groupRepository: groups,
            clock: AddMatchFixedClock(now: Date(timeIntervalSince1970: 100)),
            requestIDs: AddMatchFixedID(value: "attempt-one")
        )
        viewModel.select(group: Self.group)
        try await waitUntil { !viewModel.state.isLoading }
        for id in ["p1", "p2"] { viewModel.addPlayer(id, to: 1) }
        for id in ["p3", "p4"] { viewModel.addPlayer(id, to: 2) }
        let setID = viewModel.state.sets[0].id
        viewModel.setScore(id: setID, team: 1, value: "21")
        viewModel.setScore(id: setID, team: 2, value: "12")

        async let first: Void = viewModel.submit()
        async let duplicate: Void = viewModel.submit()
        _ = await (first, duplicate)
        #expect(viewModel.state.errorMessage != nil)

        await viewModel.submit()

        let attempts = await matches.attempts
        #expect(attempts.count == 2)
        #expect(attempts.map(\.id) == ["attempt-one", "attempt-one"])
        #expect(attempts[0].request == attempts[1].request)
        #expect(viewModel.state.successfulMatchID == "recorded")
    }

    private static let group = PlayGroup(
        id: "group", name: "Group", avatarColor: "#9ADE28", sportCode: "badminton_doubles",
        memberCount: 4, matchCount: 0, myRole: .member, sessionStart: nil, sessionEnd: nil
    )

    private func waitUntil(_ predicate: @escaping @MainActor () -> Bool) async throws {
        for _ in 0..<100 {
            if predicate() { return }
            try await Task.sleep(for: .milliseconds(5))
        }
        Issue.record("Timed out waiting for the roster")
    }
}

private struct AddMatchFixedClock: PlayboardClock { let now: Date }
private struct AddMatchFixedID: MatchRequestIDGenerating { let value: String; func next() -> String { value } }

private actor AddMatchRepositoryFake: MatchRepository {
    struct Attempt: Sendable { let id: String; let request: RecordMatchRequest }
    private(set) var attempts: [Attempt] = []
    func matches(groupID: String, cursor: String?, mine: Bool) async throws -> MatchPage { MatchPage(matches: [], nextCursor: nil) }
    func detail(groupID: String, matchID: String) async throws -> MatchDetail { fatalError("unused") }
    func record(groupID: String, request: RecordMatchRequest, requestID: String) async throws -> MatchDetail {
        attempts.append(Attempt(id: requestID, request: request))
        if attempts.count == 1 { throw MatchRepositoryError.offline }
        return AddMatchFixtures.detail
    }
}

private actor AddMatchGroupRepositoryFake: GroupRepository {
    func loadGroups() async throws -> [PlayGroup] { [] }
    func selectedGroupID() async -> String? { nil }
    func selectGroup(_ id: String?) async {}
    func createGroup(name: String) async throws -> PlayGroup { fatalError("unused") }
    func joinGroup(code: String) async throws -> PlayGroup { fatalError("unused") }
    func renameGroup(id: String, name: String) async throws -> PlayGroup { fatalError("unused") }
    func createInvite(groupID: String) async throws -> GroupInvite { fatalError("unused") }
    func loadRoster(groupID: String) async throws -> GroupRoster { AddMatchFixtures.roster }
    func addMember(groupID: String, email: String, displayName: String) async throws -> GroupMember { fatalError("unused") }
    func removeMember(groupID: String, userID: String) async throws {}
    func changeRole(groupID: String, userID: String, role: GroupRole) async throws -> GroupMember { fatalError("unused") }
    func updateSession(groupID: String, start: String?, end: String?) async throws -> PlayGroup { fatalError("unused") }
}

private enum AddMatchFixtures {
    static let roster = GroupRoster(members: (1...4).map {
        GroupMember(userID: "p\($0)", displayName: "Player \($0)", photoURL: nil, avatarID: nil, avatarColor: "#9ADE28", role: .member)
    }, guests: [])
    static let detail = MatchDetail(
        id: "recorded", playedAt: Date(timeIntervalSince1970: 100),
        teams: [], sets: [], recordedBy: MatchActor(userID: "p1", displayName: "Player 1"),
        recordedAt: Date(timeIntervalSince1970: 100), events: []
    )
}
