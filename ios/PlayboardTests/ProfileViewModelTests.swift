import Foundation
import Testing
@testable import Playboard

@Suite("Profile view model")
@MainActor
struct ProfileViewModelTests {
    @Test("Loads own stats and attendance for the active group")
    func loadsOwnProfile() async throws {
        let repository = ProfileRepositoryFake()
        let viewModel = ProfileViewModel(repository: repository, user: Self.user, clock: SeptemberClock(), grid: Self.grid)
        viewModel.select(group: Self.group)
        try await waitUntil { !viewModel.state.attendanceDays.isEmpty }

        #expect(viewModel.state.isOwnProfile)
        #expect(viewModel.state.stats?.userID == "u1")
        #expect(viewModel.state.attendanceMonths.count == 3)
        #expect(viewModel.state.recentMatches.first?.isWin == true)
        #expect(await repository.statsRequests == ["u1"])
    }

    @Test("A drill-down loads the viewed player and is read-only")
    func loadsViewedPlayer() async throws {
        let repository = ProfileRepositoryFake()
        let viewModel = ProfileViewModel(repository: repository, user: Self.user, viewedUserID: "u2", clock: SeptemberClock(), grid: Self.grid)
        viewModel.select(group: Self.group)
        try await waitUntil { viewModel.state.stats != nil }

        #expect(!viewModel.state.isOwnProfile)
        #expect(viewModel.state.displayName == "Player u2")
        viewModel.beginRename()
        #expect(viewModel.state.renameSheet == nil)
    }

    @Test("Attendance failure leaves the stats visible")
    func toleratesAttendanceFailure() async throws {
        let repository = ProfileRepositoryFake(failAttendance: true)
        let viewModel = ProfileViewModel(repository: repository, user: Self.user, clock: SeptemberClock(), grid: Self.grid)
        viewModel.select(group: Self.group)
        try await waitUntil { viewModel.state.stats != nil }
        try await Task.sleep(for: .milliseconds(20))
        #expect(viewModel.state.errorMessage == nil)
        #expect(viewModel.state.attendanceDays.isEmpty)
    }

    @Test("Rename and avatar changes update the session identity")
    func editsOwnIdentity() async throws {
        let repository = ProfileRepositoryFake()
        let updates = UserUpdates()
        let viewModel = ProfileViewModel(repository: repository, user: Self.user, clock: SeptemberClock(), grid: Self.grid,
                                         onUserUpdated: { updates.values.append($0) })
        viewModel.beginRename()
        viewModel.setRenameInput("  Priya K  ")
        await viewModel.submitRename()
        #expect(viewModel.state.renameSheet == nil)
        #expect(viewModel.state.displayName == "Priya K")

        await viewModel.selectAvatar("avatar9")
        #expect(viewModel.state.avatarID == "avatar9")
        #expect(updates.values.map(\.displayName) == ["Priya K", "Priya K"])
    }

    @Test("A failed rename keeps the sheet open for retry")
    func failedRename() async {
        let repository = ProfileRepositoryFake(failWrites: true)
        let viewModel = ProfileViewModel(repository: repository, user: Self.user, clock: SeptemberClock(), grid: Self.grid)
        viewModel.beginRename()
        viewModel.setRenameInput("New")
        await viewModel.submitRename()
        #expect(viewModel.state.renameSheet?.hasFailed == true)
        await viewModel.uploadPhoto(Data([1]), mimeType: "image/jpeg")
        #expect(viewModel.state.updateError != nil)
        #expect(!viewModel.state.isUpdatingAvatar)
    }

    @Test("Partners load on expand")
    func expandsPartners() async throws {
        let repository = ProfileRepositoryFake()
        let viewModel = ProfileViewModel(repository: repository, user: Self.user, clock: SeptemberClock(), grid: Self.grid)
        viewModel.select(group: Self.group)
        try await waitUntil { viewModel.state.stats != nil }
        viewModel.togglePartners()
        try await waitUntil { !viewModel.state.partners.isEmpty }
        viewModel.togglePartners()
        #expect(!viewModel.state.partnersExpanded)
    }

    private func waitUntil(_ predicate: @escaping @MainActor () -> Bool) async throws {
        for _ in 0..<100 {
            if predicate() { return }
            try await Task.sleep(for: .milliseconds(5))
        }
        Issue.record("Timed out waiting for profile state")
    }

    nonisolated static let user = AuthenticatedUser(id: "u1", displayName: "Priya", email: "p@example.com", photoURL: nil,
                                        avatarID: "avatar1", avatarColor: "#9ADE28", authProviders: [.google])
    nonisolated static let group = PlayGroup(id: "g1", name: "Smashers", avatarColor: "#9ADE28", sportCode: "badminton_doubles",
                                 memberCount: 4, matchCount: 2, myRole: .member, sessionStart: nil, sessionEnd: nil)
    nonisolated static var grid: AttendanceGrid {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "UTC")!
        return AttendanceGrid(calendar: calendar)
    }
}

@MainActor
private final class UserUpdates {
    var values: [AuthenticatedUser] = []
}

/// 2026-09-15T12:00:00Z.
struct SeptemberClock: PlayboardClock {
    var now: Date { Date(timeIntervalSince1970: 1_789_473_600) }
}

private actor ProfileRepositoryFake: ProfileRepository {
    private let failAttendance: Bool
    private let failWrites: Bool
    private(set) var statsRequests: [String] = []
    private var user = ProfileViewModelTests.user

    init(failAttendance: Bool = false, failWrites: Bool = false) {
        self.failAttendance = failAttendance
        self.failWrites = failWrites
    }

    func stats(groupID: String, userID: String) async throws -> PlayerStats {
        statsRequests.append(userID)
        let match = MatchSummary(
            id: "m1", playedAt: Date(timeIntervalSince1970: 1_789_300_000),
            teams: [
                MatchTeam(teamNo: 1, isWinner: true, players: [Self.player(userID), Self.player("u3")]),
                MatchTeam(teamNo: 2, isWinner: false, players: [Self.player("u4"), Self.player("u5")])
            ],
            sets: [MatchSet(setNo: 1, team1Score: 21, team2Score: 15)]
        )
        return PlayerStats(userID: userID, displayName: "Player \(userID)", photoURL: nil, avatarColor: "#9ADE28",
                           matchesPlayed: 1, wins: 1, losses: 0, pointsFor: 21, pointsAgainst: 15, winRate: 1,
                           currentStreak: 1, bestStreak: 1, recentMatches: [match])
    }

    func partners(groupID: String, userID: String, window: DateInterval?) async throws -> [PartnerStats] {
        [PartnerStats(userID: "u3", displayName: "Dev", avatarColor: "#FF9F43", gamesTogether: 1, winsTogether: 1, winRate: 1)]
    }

    func attendance(groupID: String, userID: String, window: DateInterval) async throws -> [Date] {
        if failAttendance { throw AuthorizedAPIError.offline }
        return [Date(timeIntervalSince1970: 1_789_300_000)]
    }

    func trophies(groupID: String, limit: Int) async throws -> [MonthlyTrophy] { [] }

    func updateDisplayName(_ name: String) async throws -> AuthenticatedUser {
        if failWrites { throw AuthorizedAPIError.offline }
        user = AuthenticatedUser(id: user.id, displayName: name, email: user.email, photoURL: nil, avatarID: user.avatarID,
                                 avatarColor: user.avatarColor, authProviders: user.authProviders)
        return user
    }

    func updateAvatar(_ avatarID: String) async throws -> AuthenticatedUser {
        if failWrites { throw AuthorizedAPIError.offline }
        user = AuthenticatedUser(id: user.id, displayName: user.displayName, email: user.email, photoURL: nil, avatarID: avatarID,
                                 avatarColor: user.avatarColor, authProviders: user.authProviders)
        return user
    }

    func uploadPhoto(_ data: Data, mimeType: String) async throws -> AuthenticatedUser {
        if failWrites { throw AuthorizedAPIError.offline }
        return user
    }

    private static func player(_ id: String) -> MatchPlayer {
        MatchPlayer(userID: id, displayName: "Player \(id)", avatarColor: "#9ADE28", photoURL: nil, avatarID: nil)
    }
}
