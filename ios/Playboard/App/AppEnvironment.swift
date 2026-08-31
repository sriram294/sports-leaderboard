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

    /// Production dependencies are created exactly once for the app process.
    static let live: AppEnvironment = {
        #if DEBUG
        let arguments = ProcessInfo.processInfo.arguments
        if let marker = arguments.firstIndex(of: "-ui-auth-scenario"), arguments.indices.contains(marker + 1) {
            return uiTest(scenario: UITestAuthScenario(rawValue: arguments[marker + 1]) ?? .failure)
        }
        #endif
        let configuration = ProviderConfiguration.from()
        let apiClient = URLSessionAPIClient()
        let sessionStore = KeychainSessionStore()
        return AppEnvironment(
            configuration: configuration,
            apiClient: apiClient,
            keyValueStore: InMemoryKeyValueStore(),
            authRepository: LiveAuthRepository(
                apiClient: apiClient,
                sessionStore: sessionStore,
                baseURL: configuration.apiBaseURL,
                clock: SystemPlayboardClock()
            ),
            googleAuthProvider: GoogleAuthProvider(clientID: configuration.googleClientID),
            appleCredentialParser: AppleCredentialParser()
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
            appleCredentialParser: AppleCredentialParser()
        )
    }

    #if DEBUG
    private static func uiTest(scenario: UITestAuthScenario) -> AppEnvironment {
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
            appleCredentialParser: AppleCredentialParser()
        )
    }
    #endif
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
#endif
