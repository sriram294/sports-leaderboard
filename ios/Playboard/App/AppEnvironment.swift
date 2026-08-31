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

    /// Production dependencies are created exactly once for the app process.
    static let live: AppEnvironment = {
        #if DEBUG
        let arguments = ProcessInfo.processInfo.arguments
        if let marker = arguments.firstIndex(of: "-ui-auth-scenario"), arguments.indices.contains(marker + 1) {
            let groupScenario = arguments.firstIndex(of: "-ui-group-scenario")
                .flatMap { arguments.indices.contains($0 + 1) ? UITestGroupScenario(rawValue: arguments[$0 + 1]) : nil }
                ?? .standard
            return uiTest(scenario: UITestAuthScenario(rawValue: arguments[marker + 1]) ?? .failure, groupScenario: groupScenario)
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
        let keyValueStore = UserDefaultsKeyValueStore()
        let selectedGroupStore = KeyValueSelectedGroupStore(store: keyValueStore)
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
                    baseURL: configuration.apiBaseURL,
                    accessToken: accessToken,
                    refreshAccessToken: { try await authRepository.refreshSession().accessToken }
                )
            }
        )
    }()

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
            groupRepositoryFactory: { _ in PreviewGroupRepository() }
        )
    }

    #if DEBUG
    private static func uiTest(scenario: UITestAuthScenario, groupScenario: UITestGroupScenario) -> AppEnvironment {
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
            groupRepositoryFactory: { _ in UITestGroupRepository(scenario: groupScenario) }
        )
    }
    #endif
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
        GroupMember(userID: "player-2", displayName: "Priya", photoURL: nil, avatarID: "avatar2", avatarColor: "#5B8CFF", role: .member)
    ], guests: [])
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
#endif
