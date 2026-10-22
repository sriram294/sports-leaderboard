// Linux stand-ins for the Apple-only types AppEnvironment wires in, so it can be type-checked.
import Foundation
@MainActor protocol AppleCredentialParsing {}
@MainActor struct AppleCredentialParser: AppleCredentialParsing {}
@MainActor final class GoogleAuthProvider: GoogleAuthProviding {
    init(clientID: String?) {}
    func signIn() async throws -> ProviderCredential { throw AuthError.cancelled }
    func signOut() {}
}
actor KeychainSessionStore: SessionStore {
    func load() throws -> AuthSession? { nil }
    func save(_ session: AuthSession) throws {}
    func clear() throws {}
}
