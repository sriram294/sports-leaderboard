import AuthenticationServices
import Combine
import Foundation

/// Owns login state transitions while provider SDKs and API work stay injected.
@MainActor
final class LoginViewModel: ObservableObject {
    @Published private(set) var state = LoginUiState.idle

    private let repository: any AuthRepository
    private let googleProvider: any GoogleAuthProviding
    private let appleParser: any AppleCredentialParsing
    private let onAuthenticated: (AuthSession) -> Void

    init(
        repository: any AuthRepository,
        googleProvider: any GoogleAuthProviding,
        appleParser: any AppleCredentialParsing,
        onAuthenticated: @escaping (AuthSession) -> Void
    ) {
        self.repository = repository
        self.googleProvider = googleProvider
        self.appleParser = appleParser
        self.onAuthenticated = onAuthenticated
    }

    func signInWithGoogle() async {
        guard !state.isLoading else { return }
        state.activeProvider = .google
        state.errorMessage = nil
        do {
            let credential = try await googleProvider.signIn()
            let session = try await repository.signInWithGoogle(credential)
            state = .idle
            onAuthenticated(session)
        } catch {
            finish(with: error)
        }
    }

    func completeAppleSignIn(_ result: Result<ASAuthorization, Error>) async {
        guard !state.isLoading else { return }
        state.activeProvider = .apple
        state.errorMessage = nil
        do {
            let authorization = try result.get()
            let credential = try appleParser.credential(from: authorization)
            await exchangeAppleCredential(credential)
        } catch let error as ASAuthorizationError where error.code == .canceled {
            finish(with: AuthError.cancelled)
        } catch {
            finish(with: error)
        }
    }

    func dismissError() {
        state.errorMessage = nil
    }

    func revealReviewerLogin() { state.isReviewerFormVisible = true }
    func setReviewerUsername(_ value: String) { state.reviewerUsername = value }
    func setReviewerPassword(_ value: String) { state.reviewerPassword = value }

    func signInAsReviewer() async {
        guard state.canSubmitReviewer else { return }
        state.activeProvider = .reviewer
        state.errorMessage = nil
        do {
            let session = try await repository.signInAsReviewer(
                username: state.reviewerUsername.trimmingCharacters(in: .whitespaces),
                password: state.reviewerPassword
            )
            state = .idle
            onAuthenticated(session)
        } catch {
            state.reviewerPassword = ""
            finish(with: error)
        }
    }

    /// Deterministic Apple exchange seam used by tests after native credential parsing.
    func signInWithAppleCredential(_ credential: ProviderCredential) async {
        guard !state.isLoading else { return }
        state.activeProvider = .apple
        state.errorMessage = nil
        await exchangeAppleCredential(credential)
    }

    private func exchangeAppleCredential(_ credential: ProviderCredential) async {
        do {
            let session = try await repository.signInWithApple(credential)
            state = .idle
            onAuthenticated(session)
        } catch {
            finish(with: error)
        }
    }

    private func finish(with error: Error) {
        let authError = error as? AuthError ?? .invalidResponse
        state.activeProvider = nil
        state.errorMessage = authError.message
    }
}
