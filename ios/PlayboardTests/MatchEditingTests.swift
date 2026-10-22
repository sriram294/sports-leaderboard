import Foundation
#if canImport(FoundationNetworking)
import FoundationNetworking
#endif
import Testing
@testable import Playboard

@Suite("Match edit and delete")
@MainActor
struct MatchEditingTests {
    @Test("Edit pre-fills the form and saves with the original played time")
    func editsMatch() async throws {
        let matches = EditableMatchRepository()
        let viewModel = AddMatchViewModel(matchRepository: matches, groupRepository: EditRosterRepository())
        viewModel.select(group: Self.group)
        try await waitUntil { !viewModel.state.roster.isEmpty }

        viewModel.startEditing(matchID: "m1")
        try await waitUntil { viewModel.state.editingPlayedAt != nil }
        #expect(viewModel.state.team1 == ["p1", "p2"])
        #expect(viewModel.state.team2 == ["p3", "left"])
        // A player who has left the group is still selectable for the edit.
        #expect(viewModel.state.member("left")?.displayName == "Former")
        #expect(viewModel.state.selectedWinner == 1)

        viewModel.setScore(id: viewModel.state.sets[0].id, team: 2, value: "19")
        await viewModel.submit()

        let edited = try #require(await matches.edits.first)
        #expect(edited.matchID == "m1")
        #expect(edited.request.playedAt == Self.playedAt)
        #expect(edited.request.sets.first?.team2Score == 19)
        #expect(await matches.records == 0)
        #expect(viewModel.state.lastSuccessWasEdit)

        viewModel.resetAfterSuccess()
        #expect(!viewModel.state.isEditing)
    }

    @Test("Cancelling an edit returns to a blank create form")
    func cancelsEdit() async throws {
        let viewModel = AddMatchViewModel(matchRepository: EditableMatchRepository(), groupRepository: EditRosterRepository())
        viewModel.select(group: Self.group)
        viewModel.startEditing(matchID: "m1")
        try await waitUntil { viewModel.state.editingPlayedAt != nil }
        viewModel.cancelEditing()
        #expect(!viewModel.state.isEditing)
        #expect(viewModel.state.team1.isEmpty)
        #expect(!viewModel.state.roster.isEmpty)
    }

    @Test("Recorders and moderators may modify; delete removes the match")
    func deletesMatch() async throws {
        let matches = EditableMatchRepository()
        let viewModel = MatchesViewModel(repository: matches, currentUserID: "p1")
        viewModel.select(group: Self.group)
        try await waitUntil { !viewModel.state.matches.isEmpty }

        #expect(viewModel.state.canModify(Self.detail(recordedBy: "p1")))
        #expect(!viewModel.state.canModify(Self.detail(recordedBy: "someone-else")))
        let admin = PlayGroup(id: "g1", name: "Smashers", avatarColor: "#9ADE28", sportCode: "badminton_doubles",
                              memberCount: 4, matchCount: 1, myRole: .admin, sessionStart: nil, sessionEnd: nil)
        viewModel.select(group: admin)
        #expect(viewModel.state.canModify(Self.detail(recordedBy: "someone-else")))

        viewModel.requestDelete(matchID: "m1")
        await viewModel.confirmDelete()
        #expect(viewModel.state.matches.isEmpty)
        #expect(viewModel.state.pendingDeletionID == nil)
        #expect(await matches.deleted == ["m1"])
        #expect(viewModel.state.lastDeletedMatchID == "m1")
    }

    @Test("Only the newest day starts expanded")
    func collapsesOlderDays() async throws {
        let viewModel = MatchesViewModel(repository: EditableMatchRepository(twoDays: true))
        viewModel.select(group: Self.group)
        try await waitUntil { viewModel.state.matches.count == 2 }
        let days = viewModel.sections().map(\.day)
        #expect(viewModel.isDayExpanded(days[0]))
        #expect(!viewModel.isDayExpanded(days[1]))
        viewModel.toggleDay(days[1])
        #expect(viewModel.isDayExpanded(days[1]))
    }

    @Test("Edit and delete hit the existing endpoints")
    func repositoryEndpoints() async throws {
        let client = EditRecordingClient(responses: [
            APIResponse(data: Data(Self.detailJSON.utf8), statusCode: 200),
            APIResponse(data: Data(), statusCode: 204)
        ])
        let repository = LiveMatchRepository(apiClient: client, baseURL: URL(string: "https://example.test/api/v1")!, accessToken: "a")
        let request = RecordMatchRequest(playedAt: Self.playedAt, teams: [], sets: [], winningTeamNo: 1)
        _ = try await repository.edit(groupID: "g1", matchID: "m1", request: request)
        try await repository.delete(groupID: "g1", matchID: "m1")
        #expect(await client.calls == ["PATCH /api/v1/groups/g1/matches/m1", "DELETE /api/v1/groups/g1/matches/m1"])
    }

    private func waitUntil(_ predicate: @escaping @MainActor () -> Bool) async throws {
        for _ in 0..<100 {
            if predicate() { return }
            try await Task.sleep(for: .milliseconds(5))
        }
        Issue.record("Timed out waiting for match state")
    }

    nonisolated static let playedAt = Date(timeIntervalSince1970: 1_789_300_000)
    nonisolated static let group = PlayGroup(id: "g1", name: "Smashers", avatarColor: "#9ADE28", sportCode: "badminton_doubles",
                                 memberCount: 4, matchCount: 1, myRole: .member, sessionStart: nil, sessionEnd: nil)

    nonisolated static func player(_ id: String, _ name: String? = nil) -> MatchPlayer {
        MatchPlayer(userID: id, displayName: name ?? id, avatarColor: "#9ADE28", photoURL: nil, avatarID: nil)
    }

    nonisolated static func detail(recordedBy: String) -> MatchDetail {
        MatchDetail(
            id: "m1", playedAt: playedAt,
            teams: [
                MatchTeam(teamNo: 1, isWinner: true, players: [player("p1"), player("p2")]),
                MatchTeam(teamNo: 2, isWinner: false, players: [player("p3"), player("left", "Former")])
            ],
            sets: [MatchSet(setNo: 1, team1Score: 21, team2Score: 15)],
            recordedBy: MatchActor(userID: recordedBy, displayName: recordedBy),
            recordedAt: playedAt, events: []
        )
    }

    nonisolated static let detailJSON = ##"{"id":"m1","playedAt":"2026-09-13T11:46:40Z","teams":[],"sets":[],"recordedBy":{"userId":"p1","displayName":"P"},"recordedAt":"2026-09-13T11:46:40.5Z","events":[]}"##
}

private actor EditableMatchRepository: MatchRepository {
    private let twoDays: Bool
    private(set) var edits: [(matchID: String, request: RecordMatchRequest)] = []
    private(set) var deleted: [String] = []
    private(set) var records = 0

    init(twoDays: Bool = false) { self.twoDays = twoDays }

    func matches(groupID: String, cursor: String?, mine: Bool) async throws -> MatchPage {
        var matches = [MatchSummary(id: "m1", playedAt: MatchEditingTests.playedAt, teams: [], sets: [])]
        if twoDays { matches.append(MatchSummary(id: "m0", playedAt: MatchEditingTests.playedAt.addingTimeInterval(-3 * 86_400), teams: [], sets: [])) }
        return MatchPage(matches: matches, nextCursor: nil)
    }
    func detail(groupID: String, matchID: String) async throws -> MatchDetail { MatchEditingTests.detail(recordedBy: "p1") }
    func record(groupID: String, request: RecordMatchRequest, requestID: String) async throws -> MatchDetail {
        records += 1
        return MatchEditingTests.detail(recordedBy: "p1")
    }
    func edit(groupID: String, matchID: String, request: RecordMatchRequest) async throws -> MatchDetail {
        edits.append((matchID, request))
        return MatchEditingTests.detail(recordedBy: "p1")
    }
    func delete(groupID: String, matchID: String) async throws { deleted.append(matchID) }
}

private actor EditRosterRepository: GroupRepository {
    func loadGroups() async throws -> [PlayGroup] { [MatchEditingTests.group] }
    func selectedGroupID() async -> String? { "g1" }
    func selectGroup(_ id: String?) async {}
    func createGroup(name: String) async throws -> PlayGroup { MatchEditingTests.group }
    func joinGroup(code: String) async throws -> PlayGroup { MatchEditingTests.group }
    func renameGroup(id: String, name: String) async throws -> PlayGroup { MatchEditingTests.group }
    func createInvite(groupID: String) async throws -> GroupInvite { GroupInvite(code: "X", expiresAt: nil) }
    func loadRoster(groupID: String) async throws -> GroupRoster {
        GroupRoster(members: ["p1", "p2", "p3", "p4"].map {
            GroupMember(userID: $0, displayName: $0, photoURL: nil, avatarID: nil, avatarColor: "#9ADE28", role: .member)
        }, guests: [])
    }
    func addMember(groupID: String, email: String, displayName: String) async throws -> GroupMember { throw GroupRepositoryError.invalidInput }
    func removeMember(groupID: String, userID: String) async throws {}
    func changeRole(groupID: String, userID: String, role: GroupRole) async throws -> GroupMember { throw GroupRepositoryError.invalidInput }
    func updateSession(groupID: String, start: String?, end: String?) async throws -> PlayGroup { MatchEditingTests.group }
}

private actor EditRecordingClient: APIClient {
    private var responses: [APIResponse]
    private(set) var calls: [String] = []
    init(responses: [APIResponse]) { self.responses = responses }
    func response(for request: URLRequest) async throws -> APIResponse {
        calls.append("\(request.httpMethod ?? "") \(request.url?.path ?? "")")
        return responses.removeFirst()
    }
}
