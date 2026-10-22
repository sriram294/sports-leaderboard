import Foundation

/// The application's explicit dependency composition root.
@MainActor
struct AppEnvironment {
    let configuration: ProviderConfiguration
    let apiClient: any APIClient
    let keyValueStore: any KeyValueStore
    let authRepository: any AuthRepository
    let googleAuthProvider: any GoogleAuthProviding
    let appleCredentialParser: any AppleCredentialParsing
    let groupRepositoryFactory: (String) -> any GroupRepository
    let leaderboardRepositoryFactory: (String) -> any LeaderboardRepository
    let matchRepositoryFactory: (String) -> any MatchRepository
    let profileRepositoryFactory: (String) -> any ProfileRepository
    let accountRepositoryFactory: (String) -> any AccountRepository
    let deviceRepositoryFactory: (String) -> any DeviceRepository
    let updateRepository: any UpdateRepository

    /// Production dependencies; ``PlayboardApp`` calls this once for the app process.
    static func live() -> AppEnvironment {
        #if DEBUG
        let arguments = ProcessInfo.processInfo.arguments
        if let marker = arguments.firstIndex(of: "-ui-auth-scenario"), arguments.indices.contains(marker + 1) {
            let groupScenario = arguments.firstIndex(of: "-ui-group-scenario")
                .flatMap { arguments.indices.contains($0 + 1) ? UITestGroupScenario(rawValue: arguments[$0 + 1]) : nil }
                ?? .standard
            let leaderboardScenario = arguments.firstIndex(of: "-ui-leaderboard-scenario")
                .flatMap { arguments.indices.contains($0 + 1) ? UITestLeaderboardScenario(rawValue: arguments[$0 + 1]) : nil }
                ?? .standard
            let matchScenario = arguments.firstIndex(of: "-ui-match-scenario")
                .flatMap { arguments.indices.contains($0 + 1) ? UITestMatchScenario(rawValue: arguments[$0 + 1]) : nil }
                ?? .standard
            return uiTest(
                scenario: UITestAuthScenario(rawValue: arguments[marker + 1]) ?? .failure,
                groupScenario: groupScenario,
                leaderboardScenario: leaderboardScenario,
                matchScenario: matchScenario
            )
        }
        #endif
        let configuration = ProviderConfiguration.from()
        let apiClient = URLSessionAPIClient()
        let sessionStore = KeychainSessionStore()
        let authRepository = LiveAuthRepository(
            apiClient: apiClient,
            sessionStore: sessionStore,
            baseURL: configuration.apiBaseURL,
            clock: SystemPlayboardClock()
        )
        // Repositories need a concrete URL; a build without one shows the login configuration notice instead.
        let baseURL = configuration.apiBaseURL ?? URL(string: "https://example.invalid/api/v1")!
        let keyValueStore = UserDefaultsKeyValueStore()
        let selectedGroupStore = KeyValueSelectedGroupStore(store: keyValueStore)
        let updateRepository = LiveUpdateRepository(
            apiClient: apiClient,
            baseURL: baseURL
        )
        return AppEnvironment(
            configuration: configuration,
            apiClient: apiClient,
            keyValueStore: keyValueStore,
            authRepository: authRepository,
            googleAuthProvider: GoogleAuthProvider(clientID: configuration.googleClientID),
            appleCredentialParser: AppleCredentialParser(),
            groupRepositoryFactory: { accessToken in
                LiveGroupRepository(
                    apiClient: apiClient,
                    selectedGroupStore: selectedGroupStore,
                    baseURL: baseURL,
                    accessToken: accessToken,
                    refreshAccessToken: { try await authRepository.refreshSession().accessToken }
                )
            },
            leaderboardRepositoryFactory: { accessToken in
                LiveLeaderboardRepository(
                    apiClient: apiClient,
                    baseURL: baseURL,
                    accessToken: accessToken,
                    refreshAccessToken: { try await authRepository.refreshSession().accessToken }
                )
            },
            matchRepositoryFactory: { accessToken in
                LiveMatchRepository(
                    apiClient: apiClient,
                    baseURL: baseURL,
                    accessToken: accessToken,
                    refreshAccessToken: { try await authRepository.refreshSession().accessToken }
                )
            },
            profileRepositoryFactory: { accessToken in
                LiveProfileRepository(
                    apiClient: apiClient,
                    baseURL: baseURL,
                    accessToken: accessToken,
                    refreshAccessToken: { try await authRepository.refreshSession().accessToken }
                )
            },
            accountRepositoryFactory: { accessToken in
                LiveAccountRepository(
                    apiClient: apiClient,
                    baseURL: baseURL,
                    accessToken: accessToken
                )
            },
            deviceRepositoryFactory: { accessToken in
                LiveDeviceRepository(
                    apiClient: apiClient,
                    baseURL: baseURL,
                    accessToken: accessToken,
                    refreshAccessToken: { try await authRepository.refreshSession().accessToken }
                )
            },
            updateRepository: updateRepository
        )
    }

    /// A deterministic environment for previews and tests.
    static func preview() -> AppEnvironment {
        let configuration = ProviderConfiguration(
            apiBaseURL: URL(string: "https://example.invalid/api/v1"),
            googleClientID: "preview-client-id"
        )
        return AppEnvironment(
            configuration: configuration,
            apiClient: StubAPIClient(data: Data()),
            keyValueStore: InMemoryKeyValueStore(),
            authRepository: PreviewAuthRepository(),
            googleAuthProvider: PreviewGoogleAuthProvider(),
            appleCredentialParser: AppleCredentialParser(),
            groupRepositoryFactory: { _ in PreviewGroupRepository() },
            leaderboardRepositoryFactory: { _ in PreviewLeaderboardRepository() },
            matchRepositoryFactory: { _ in PreviewMatchRepository() }
            ,profileRepositoryFactory: { _ in PreviewProfileRepository() }
            ,accountRepositoryFactory: { _ in PreviewAccountRepository() }
            ,deviceRepositoryFactory: { _ in PreviewDeviceRepository() }
            ,updateRepository: PreviewUpdateRepository()
        )
    }

    #if DEBUG
    private static func uiTest(
        scenario: UITestAuthScenario,
        groupScenario: UITestGroupScenario,
        leaderboardScenario: UITestLeaderboardScenario,
        matchScenario: UITestMatchScenario
    ) -> AppEnvironment {
        let configuration = ProviderConfiguration(
            apiBaseURL: URL(string: "https://example.invalid/api/v1"),
            googleClientID: "ui-test-client-id"
        )
        return AppEnvironment(
            configuration: configuration,
            apiClient: StubAPIClient(data: Data()),
            keyValueStore: InMemoryKeyValueStore(),
            authRepository: UITestAuthRepository(scenario: scenario),
            googleAuthProvider: UITestGoogleAuthProvider(scenario: scenario),
            appleCredentialParser: AppleCredentialParser(),
            groupRepositoryFactory: { _ in UITestGroupRepository(scenario: groupScenario) },
            leaderboardRepositoryFactory: { _ in UITestLeaderboardRepository(scenario: leaderboardScenario) },
            matchRepositoryFactory: { _ in UITestMatchRepository(scenario: matchScenario) }
            ,profileRepositoryFactory: { _ in PreviewProfileRepository() }
            ,accountRepositoryFactory: { _ in PreviewAccountRepository() }
            ,deviceRepositoryFactory: { _ in PreviewDeviceRepository() }
            ,updateRepository: PreviewUpdateRepository()
        )
    }
    #endif
}

private actor PreviewLeaderboardRepository: LeaderboardRepository {
    func leaderboard(groupID: String, window: DateInterval?) async throws -> Leaderboard {
        Leaderboard(rankings: [
            LeaderboardEntry(rank: 1, userID: "ui-user", displayName: "Test Player", photoURL: nil, avatarID: "avatar1", avatarColor: "#9ADE28", gamesPlayed: 12, wins: 9, losses: 3, pointsFor: 504, pointsAgainst: 420, winRate: 0.75, currentStreak: 3, bestStreak: 5, rating: 46.2, provisional: false, recentForm: [true, false, true, true]),
            LeaderboardEntry(rank: 2, userID: "player-2", displayName: "Priya", photoURL: nil, avatarID: "avatar2", avatarColor: "#5B8CFF", gamesPlayed: 10, wins: 7, losses: 3, pointsFor: 420, pointsAgainst: 390, winRate: 0.7, currentStreak: 2, bestStreak: 4, rating: 41.0, provisional: false, recentForm: [true, true, false])
        ], minGamesToRank: 3)
    }
}

private actor PreviewMatchRepository: MatchRepository {
    func matches(groupID: String, cursor: String?, mine: Bool) async throws -> MatchPage {
        MatchPage(matches: [.preview], nextCursor: nil)
    }
    func detail(groupID: String, matchID: String) async throws -> MatchDetail { .preview }
    func record(groupID: String, request: RecordMatchRequest, requestID: String) async throws -> MatchDetail { .preview }
}

private actor PreviewProfileRepository: ProfileRepository {
    func stats(groupID: String, userID: String) async throws -> PlayerStats {
        PlayerStats(userID: userID, displayName: "Test Player", photoURL: nil, avatarID: "avatar1", avatarColor: "#9ADE28", matchesPlayed: 12, wins: 9, losses: 3, pointsFor: 504, pointsAgainst: 420, winRate: 0.75, currentStreak: 3, bestStreak: 5, recentMatches: [.preview],
                    trophies: [MonthlyTrophy(month: "2026-08", userID: userID, displayName: "Test Player", avatarID: "avatar1", avatarColor: "#9ADE28")],
                    monthlyFinishes: [PlayerStats.MonthlyFinish(month: "2026-07", rank: 3, qualifiedPlayers: 6), PlayerStats.MonthlyFinish(month: "2026-08", rank: 1, qualifiedPlayers: 6)])
    }
    func partners(groupID: String, userID: String, window: DateInterval?) async throws -> [PartnerStats] { [PartnerStats(userID: "player-3", displayName: "Dev", avatarColor: "#FF9F43", gamesTogether: 4, winsTogether: 3, winRate: 0.75)] }
    func attendance(groupID: String, userID: String, window: DateInterval) async throws -> [Date] { [MatchSummary.preview.playedAt] }
    func trophies(groupID: String, limit: Int) async throws -> [MonthlyTrophy] {
        [MonthlyTrophy(month: "2026-08", userID: "ui-user", displayName: "Test Player", avatarID: "avatar1", avatarColor: "#9ADE28", rating: 46.2, gamesPlayed: 12, wins: 9)]
    }
    func updateDisplayName(_ name: String) async throws -> AuthenticatedUser { Self.user(name: name, avatarID: "avatar1") }
    func updateAvatar(_ avatarID: String) async throws -> AuthenticatedUser { Self.user(name: "Test Player", avatarID: avatarID) }
    func uploadPhoto(_ data: Data, mimeType: String) async throws -> AuthenticatedUser { Self.user(name: "Test Player", avatarID: nil) }
    private static func user(name: String, avatarID: String?) -> AuthenticatedUser {
        AuthenticatedUser(id: "ui-user", displayName: name, email: "player@example.com", photoURL: nil, avatarID: avatarID, avatarColor: "#9ADE28", authProviders: [.google])
    }
}

private actor PreviewDeviceRepository: DeviceRepository {
    func register(token: String) async throws {}
    func unregister(token: String) async throws {}
}

private actor PreviewUpdateRepository: UpdateRepository {
    func latest() async throws -> AppUpdate {
        AppUpdate(versionCode: nil, versionName: nil, downloadURL: nil, available: false)
    }
}

private actor PreviewAccountRepository: AccountRepository {
    func deleteAccount() async throws {}
}

private actor PreviewGroupRepository: GroupRepository {
    private var selectedID: String? = "preview-group"
    private var groups = [PlayGroup.preview]

    func loadGroups() async throws -> [PlayGroup] { groups }
    func selectedGroupID() async -> String? { selectedID }
    func selectGroup(_ id: String?) async { selectedID = id }
    func createGroup(name: String) async throws -> PlayGroup { PlayGroup.preview }
    func joinGroup(code: String) async throws -> PlayGroup { PlayGroup.preview }
    func renameGroup(id: String, name: String) async throws -> PlayGroup { PlayGroup.preview }
    func createInvite(groupID: String) async throws -> GroupInvite { GroupInvite(code: "SMASH42", expiresAt: nil) }
    func loadRoster(groupID: String) async throws -> GroupRoster { .preview }
    func addMember(groupID: String, email: String, displayName: String) async throws -> GroupMember { GroupRoster.preview.members[0] }
    func removeMember(groupID: String, userID: String) async throws {}
    func changeRole(groupID: String, userID: String, role: GroupRole) async throws -> GroupMember { GroupRoster.preview.members[0] }
    func updateSession(groupID: String, start: String?, end: String?) async throws -> PlayGroup { PlayGroup.preview }
}

private extension PlayGroup {
    static let preview = PlayGroup(
        id: "preview-group", name: "Saturday Smashers", avatarColor: "#9ADE28",
        sportCode: "badminton_doubles", memberCount: 6, matchCount: 14, myRole: .owner,
        sessionStart: "19:00", sessionEnd: "21:00"
    )
}

private extension GroupRoster {
    static let preview = GroupRoster(members: [
        GroupMember(userID: "ui-user", displayName: "Test Player", photoURL: nil, avatarID: "avatar1", avatarColor: "#9ADE28", role: .owner),
        GroupMember(userID: "player-2", displayName: "Priya", photoURL: nil, avatarID: "avatar2", avatarColor: "#5B8CFF", role: .member),
        GroupMember(userID: "player-3", displayName: "Dev", photoURL: nil, avatarID: "avatar3", avatarColor: "#FF9F43", role: .member),
        GroupMember(userID: "player-4", displayName: "Kiran", photoURL: nil, avatarID: "avatar4", avatarColor: "#A78BFA", role: .member)
    ], guests: [])
}

private extension MatchSummary {
    static let preview = MatchSummary(
        id: "match-preview",
        playedAt: Date(timeIntervalSince1970: 1_786_262_280),
        teams: [.previewOne, .previewTwo],
        sets: [MatchSet(setNo: 1, team1Score: 21, team2Score: 14), MatchSet(setNo: 2, team1Score: 21, team2Score: 18)]
    )
}

private extension MatchDetail {
    static let preview = MatchDetail(
        id: MatchSummary.preview.id,
        playedAt: MatchSummary.preview.playedAt,
        teams: MatchSummary.preview.teams,
        sets: MatchSummary.preview.sets,
        recordedBy: MatchActor(userID: "ui-user", displayName: "Test Player"),
        recordedAt: MatchSummary.preview.playedAt,
        events: [MatchEvent(userID: "ui-user", displayName: "Test Player", action: "created", createdAt: MatchSummary.preview.playedAt)],
        ratingChanges: [
            MatchRatingChange(userID: "ui-user", ratingDelta: 4.2),
            MatchRatingChange(userID: "player-3", ratingDelta: 3.8),
            MatchRatingChange(userID: "player-2", ratingDelta: -2.7),
            MatchRatingChange(userID: "player-4", ratingDelta: nil)
        ]
    )
}

private extension MatchTeam {
    static let previewOne = MatchTeam(teamNo: 1, isWinner: true, players: [
        MatchPlayer(userID: "ui-user", displayName: "Test Player", avatarColor: "#9ADE28", photoURL: nil, avatarID: "avatar1"),
        MatchPlayer(userID: "player-3", displayName: "Dev", avatarColor: "#FF9F43", photoURL: nil, avatarID: "avatar3")
    ])
    static let previewTwo = MatchTeam(teamNo: 2, isWinner: false, players: [
        MatchPlayer(userID: "player-2", displayName: "Priya", avatarColor: "#5B8CFF", photoURL: nil, avatarID: "avatar2"),
        MatchPlayer(userID: "player-4", displayName: "Kiran", avatarColor: "#A78BFA", photoURL: nil, avatarID: "avatar4")
    ])
}

private actor PreviewAuthRepository: AuthRepository {
    func restoreSession() async -> AuthSession? { nil }
    func signInWithGoogle(_ credential: ProviderCredential) async throws -> AuthSession { throw AuthError.cancelled }
    func signInWithApple(_ credential: ProviderCredential) async throws -> AuthSession { throw AuthError.cancelled }
    func refreshSession() async throws -> AuthSession { throw AuthError.expiredSession }
    func logout() async {}
}

@MainActor
private final class PreviewGoogleAuthProvider: GoogleAuthProviding {
    func signIn() async throws -> ProviderCredential {
        throw AuthError.cancelled
    }

    func signOut() {}
}

#if DEBUG
private enum UITestAuthScenario: String, Sendable {
    case success
    case cancellation
    case failure
    case restore
}

private enum UITestGroupScenario: String, Sendable {
    case standard
    case empty
    case failure
    case twoGroups
}

private enum UITestLeaderboardScenario: String, Sendable {
    case standard
    case dense
    case empty
    case failure
}

private enum UITestMatchScenario: String, Sendable {
    case standard
    case empty
    case failure
    case retry
}

private actor UITestAuthRepository: AuthRepository {
    private let scenario: UITestAuthScenario

    init(scenario: UITestAuthScenario) {
        self.scenario = scenario
    }

    func restoreSession() async -> AuthSession? {
        scenario == .restore ? Self.session : nil
    }

    func signInWithGoogle(_ credential: ProviderCredential) async throws -> AuthSession {
        if scenario == .failure {
            throw AuthError.offline
        }
        return Self.session
    }

    func signInWithApple(_ credential: ProviderCredential) async throws -> AuthSession {
        try await signInWithGoogle(credential)
    }

    func refreshSession() async throws -> AuthSession {
        Self.session
    }

    func logout() async {}

    private static let session = AuthSession(
        accessToken: "ui-access",
        refreshToken: "ui-refresh",
        accessTokenExpiresAt: Date.distantFuture,
        user: AuthenticatedUser(
            id: "ui-user",
            displayName: "Test Player",
            email: "player@example.com",
            photoURL: nil,
            avatarID: "avatar1",
            avatarColor: "#9ADE28",
            authProviders: [.google]
        )
    )
}

@MainActor
private final class UITestGoogleAuthProvider: GoogleAuthProviding {
    private let scenario: UITestAuthScenario

    init(scenario: UITestAuthScenario) {
        self.scenario = scenario
    }

    func signIn() async throws -> ProviderCredential {
        if scenario == .cancellation {
            throw AuthError.cancelled
        }
        return ProviderCredential(identityToken: "ui-provider-token", givenName: nil, familyName: nil)
    }

    func signOut() {}
}

private actor UITestGroupRepository: GroupRepository {
    private let scenario: UITestGroupScenario
    private var selectedID: String?
    private var groups: [PlayGroup]

    init(scenario: UITestGroupScenario) {
        self.scenario = scenario
        let second = PlayGroup(id: "second-group", name: "Sunday Shuttles", avatarColor: "#5B8CFF", sportCode: "badminton_doubles", memberCount: 4, matchCount: 8, myRole: .admin, sessionStart: nil, sessionEnd: nil)
        groups = switch scenario {
        case .empty: []
        case .twoGroups: [.preview, second]
        case .standard, .failure: [.preview]
        }
        selectedID = groups.first?.id
    }

    func loadGroups() async throws -> [PlayGroup] {
        if scenario == .failure { throw GroupRepositoryError.offline }
        return groups
    }
    func selectedGroupID() async -> String? { selectedID }
    func selectGroup(_ id: String?) async { selectedID = id }
    func createGroup(name: String) async throws -> PlayGroup { add(name: name) }
    func joinGroup(code: String) async throws -> PlayGroup { add(name: "Joined Club") }
    func renameGroup(id: String, name: String) async throws -> PlayGroup {
        guard let index = groups.firstIndex(where: { $0.id == id }) else { throw GroupRepositoryError.invalidResponse }
        groups[index].name = name
        return groups[index]
    }
    func createInvite(groupID: String) async throws -> GroupInvite { GroupInvite(code: "SMASH42", expiresAt: nil) }
    func loadRoster(groupID: String) async throws -> GroupRoster { .preview }
    func addMember(groupID: String, email: String, displayName: String) async throws -> GroupMember { GroupRoster.preview.members[1] }
    func removeMember(groupID: String, userID: String) async throws {}
    func changeRole(groupID: String, userID: String, role: GroupRole) async throws -> GroupMember { GroupRoster.preview.members[1] }
    func updateSession(groupID: String, start: String?, end: String?) async throws -> PlayGroup {
        guard let index = groups.firstIndex(where: { $0.id == groupID }) else { throw GroupRepositoryError.invalidResponse }
        groups[index].sessionStart = start
        groups[index].sessionEnd = end
        return groups[index]
    }

    private func add(name: String) -> PlayGroup {
        let group = PlayGroup(id: "created-group", name: name, avatarColor: "#9ADE28", sportCode: "badminton_doubles", memberCount: 1, matchCount: 0, myRole: .owner, sessionStart: nil, sessionEnd: nil)
        groups.append(group)
        selectedID = group.id
        return group
    }
}

private actor UITestLeaderboardRepository: LeaderboardRepository {
    private let scenario: UITestLeaderboardScenario
    init(scenario: UITestLeaderboardScenario) { self.scenario = scenario }

    func leaderboard(groupID: String, window: DateInterval?) async throws -> Leaderboard {
        if scenario == .failure { throw GroupRepositoryError.offline }
        if scenario == .empty { return Leaderboard(rankings: [], minGamesToRank: 3) }
        let count = scenario == .dense ? 12 : 4
        let rows = (1...count).map { Self.entry(rank: $0, count: count) }
        return Leaderboard(rankings: rows, minGamesToRank: 3)
    }

    /// Deterministic fixture row; explicit types keep the type checker fast.
    private static func entry(rank: Int, count: Int) -> LeaderboardEntry {
        let even = rank.isMultiple(of: 2)
        let rankValue = Double(rank)
        let winRate: Double = max(0.1, 0.82 - rankValue * 0.04)
        let rating: Double = max(8.0, 58.0 - rankValue * 3.2)
        let gamesPlayed: Int = max(1, 14 - rank)
        let wins: Int = max(1, 10 - rank / 2)
        let pointsFor: Int = 500 - rank * 12
        let pointsAgainst: Int = 390 + rank * 5
        let currentStreak: Int = max(0, 5 - rank)
        let bestStreak: Int = max(1, 7 - rank / 2)
        let displayName = rank == 1 ? "Priya" : "Player \(rank)"
        let photoURL: String? = rank == 2 ? "https://example.invalid/missing.png" : nil
        return LeaderboardEntry(
            rank: rank,
            userID: "player-\(rank)",
            displayName: displayName,
            photoURL: photoURL,
            avatarID: "avatar\(rank % 16)",
            avatarColor: even ? "#5B8CFF" : "#9ADE28",
            gamesPlayed: gamesPlayed,
            wins: wins,
            losses: rank / 2,
            pointsFor: pointsFor,
            pointsAgainst: pointsAgainst,
            winRate: winRate,
            currentStreak: currentStreak,
            bestStreak: bestStreak,
            rating: rating,
            provisional: rank == count,
            recentForm: [true, even, false, true]
        )
    }
}

private actor UITestMatchRepository: MatchRepository {
    private let scenario: UITestMatchScenario
    private var recordAttempts = 0
    init(scenario: UITestMatchScenario) { self.scenario = scenario }

    func matches(groupID: String, cursor: String?, mine: Bool) async throws -> MatchPage {
        if scenario == .failure { throw MatchRepositoryError.offline }
        return MatchPage(matches: scenario == .empty ? [] : [.preview], nextCursor: nil)
    }

    func detail(groupID: String, matchID: String) async throws -> MatchDetail {
        if scenario == .failure { throw MatchRepositoryError.offline }
        return .preview
    }

    func record(groupID: String, request: RecordMatchRequest, requestID: String) async throws -> MatchDetail {
        recordAttempts += 1
        if scenario == .failure || (scenario == .retry && recordAttempts == 1) {
            throw MatchRepositoryError.offline
        }
        return .preview
    }
}
#endif
