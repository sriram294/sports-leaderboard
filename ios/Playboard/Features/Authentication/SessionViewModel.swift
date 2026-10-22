import Combine
import Foundation

/// Root authentication routing states for restore, recovery, login, and an active session.
enum SessionUiState: Equatable, Sendable {
    case restoring
    case signedOut
    case signedIn(AuthSession)
    case recovery(message: String)
}

/// Owns process-level session restoration and logout routing.
@MainActor
final class SessionViewModel: ObservableObject {
    @Published private(set) var state: SessionUiState = .restoring

    private let repository: any AuthRepository
    private let googleProvider: any GoogleAuthProviding
    private var attemptedRestore = false

    init(repository: any AuthRepository, googleProvider: any GoogleAuthProviding) {
        self.repository = repository
        self.googleProvider = googleProvider
    }

    func restoreIfNeeded() async {
        guard !attemptedRestore else { return }
        attemptedRestore = true
        await restore()
    }

    func retryRestore() async {
        state = .restoring
        await restore()
    }

    func accept(_ session: AuthSession) {
        state = .signedIn(session)
    }

    /// Applies a profile change (rename, avatar) to the live and persisted session.
    func updateUser(_ user: AuthenticatedUser) async {
        guard case .signedIn(let session) = state else { return }
        await repository.updateUser(user)
        state = .signedIn(AuthSession(
            accessToken: session.accessToken,
            refreshToken: session.refreshToken,
            accessTokenExpiresAt: session.accessTokenExpiresAt,
            user: user
        ))
    }

    func signOut() async {
        await repository.logout()
        googleProvider.signOut()
        state = .signedOut
    }

    private func restore() async {
        do {
            if let session = try await repository.restoreSession() {
                state = .signedIn(session)
            } else {
                state = .signedOut
            }
        } catch let error as AuthError {
            if error == .expiredSession {
                state = .signedOut
            } else {
                state = .recovery(message: error.message)
            }
        } catch {
            state = .recovery(message: AuthError.invalidResponse.message)
        }
    }
}
