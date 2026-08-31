import Foundation
import Testing
@testable import Playboard

@MainActor
@Suite("Authentication presentation")
struct AuthenticationViewModelTests {
    @Test("Google success advances to an authenticated session")
    func googleSuccess() async {
        let session = fixtureSession()
        let repository = FakeAuthRepository(googleResult: .success(session))
        let provider = FakeGoogleProvider(result: .success(
            ProviderCredential(identityToken: "google", givenName: nil, familyName: nil)
        ))
        var received: AuthSession?
        let viewModel = LoginViewModel(
            repository: repository,
            googleProvider: provider,
            appleParser: AppleCredentialParser(),
            onAuthenticated: { received = $0 }
        )

        await viewModel.signInWithGoogle()

        #expect(viewModel.state == .idle)
        #expect(received == session)
    }

    @Test("Provider cancellation is recoverable")
    func googleCancellation() async {
        let viewModel = LoginViewModel(
            repository: FakeAuthRepository(),
            googleProvider: FakeGoogleProvider(result: .failure(AuthError.cancelled)),
            appleParser: AppleCredentialParser(),
            onAuthenticated: { _ in Issue.record("Unexpected authentication") }
        )

        await viewModel.signInWithGoogle()

        #expect(viewModel.state.activeProvider == nil)
        #expect(viewModel.state.errorMessage == AuthError.cancelled.message)
        viewModel.dismissError()
        #expect(viewModel.state == .idle)
    }

    @Test("Apple API failure exposes its recovery message")
    func appleFailure() async {
        let repository = FakeAuthRepository(appleResult: .failure(AuthError.offline))
        let viewModel = LoginViewModel(
            repository: repository,
            googleProvider: FakeGoogleProvider(result: .failure(AuthError.cancelled)),
            appleParser: AppleCredentialParser(),
            onAuthenticated: { _ in Issue.record("Unexpected authentication") }
        )

        await viewModel.signInWithAppleCredential(
            ProviderCredential(identityToken: "apple", givenName: "Priya", familyName: "Shah")
        )

        #expect(viewModel.state.errorMessage == AuthError.offline.message)
    }

    @Test("Restore and logout route deterministically")
    func restoreAndLogout() async {
        let session = fixtureSession()
        let repository = FakeAuthRepository(restoreResult: .success(session))
        let provider = FakeGoogleProvider(result: .failure(AuthError.cancelled))
        let viewModel = SessionViewModel(repository: repository, googleProvider: provider)

        await viewModel.restoreIfNeeded()
        #expect(viewModel.state == .signedIn(session))

        await viewModel.signOut()
        #expect(viewModel.state == .signedOut)
        let logoutCount = await repository.logoutCount
        #expect(logoutCount == 1)
        #expect(provider.signOutCount == 1)
    }
}

private actor FakeAuthRepository: AuthRepository {
    let restoreResult: Result<AuthSession?, Error>
    let googleResult: Result<AuthSession, Error>
    let appleResult: Result<AuthSession, Error>
    private(set) var logoutCount = 0

    init(
        restoreResult: Result<AuthSession?, Error> = .success(nil),
        googleResult: Result<AuthSession, Error> = .failure(AuthError.rejected("Unused")),
        appleResult: Result<AuthSession, Error> = .failure(AuthError.rejected("Unused"))
    ) {
        self.restoreResult = restoreResult
        self.googleResult = googleResult
        self.appleResult = appleResult
    }

    func restoreSession() async throws -> AuthSession? { try restoreResult.get() }
    func signInWithGoogle(_ credential: ProviderCredential) async throws -> AuthSession { try googleResult.get() }
    func signInWithApple(_ credential: ProviderCredential) async throws -> AuthSession { try appleResult.get() }
    func refreshSession() async throws -> AuthSession { throw AuthError.expiredSession }
    func logout() async { logoutCount += 1 }
}

@MainActor
private final class FakeGoogleProvider: GoogleAuthProviding {
    let result: Result<ProviderCredential, Error>
    private(set) var signOutCount = 0

    init(result: Result<ProviderCredential, Error>) {
        self.result = result
    }

    func signIn() async throws -> ProviderCredential { try result.get() }
    func signOut() { signOutCount += 1 }
}

private func fixtureSession() -> AuthSession {
    AuthSession(
        accessToken: "access",
        refreshToken: "refresh",
        accessTokenExpiresAt: Date(timeIntervalSince1970: 2_000_000_000),
        user: AuthenticatedUser(
            id: "user-1",
            displayName: "Priya",
            email: "priya@example.com",
            photoURL: nil,
            avatarID: "avatar1",
            avatarColor: "#9ADE28",
            authProviders: [.google]
        )
    )
}
