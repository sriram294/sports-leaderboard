import Foundation
import Security

/// Persisted Playboard access/refresh pair and the identity needed during refresh.
struct AuthSession: Codable, Equatable, Sendable {
    let accessToken: String
    let refreshToken: String
    let accessTokenExpiresAt: Date
    let user: AuthenticatedUser
}

/// Secure session persistence boundary used by the authentication repository.
protocol SessionStore: Sendable {
    func load() async throws -> AuthSession?
    func save(_ session: AuthSession) async throws
    func clear() async throws
}

/// Keychain-backed token storage scoped to this app installation.
actor KeychainSessionStore: SessionStore {
    private let service: String
    private let account = "playboard-auth-session"
    private let encoder = JSONEncoder()
    private let decoder = JSONDecoder()

    init(service: String = Bundle.main.bundleIdentifier ?? "com.org.playboard") {
        self.service = service
    }

    func load() throws -> AuthSession? {
        var query = baseQuery
        query[kSecReturnData as String] = true
        query[kSecMatchLimit as String] = kSecMatchLimitOne

        var result: CFTypeRef?
        let status = SecItemCopyMatching(query as CFDictionary, &result)
        if status == errSecItemNotFound {
            return nil
        }
        guard status == errSecSuccess, let data = result as? Data else {
            throw SessionStoreError.keychain(status)
        }
        return try decoder.decode(AuthSession.self, from: data)
    }

    func save(_ session: AuthSession) throws {
        let data = try encoder.encode(session)
        let attributes: [String: Any] = [
            kSecValueData as String: data,
            kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,
        ]
        let status = SecItemUpdate(baseQuery as CFDictionary, attributes as CFDictionary)
        if status == errSecItemNotFound {
            var insertion = baseQuery
            attributes.forEach { insertion[$0.key] = $0.value }
            let insertionStatus = SecItemAdd(insertion as CFDictionary, nil)
            guard insertionStatus == errSecSuccess else {
                throw SessionStoreError.keychain(insertionStatus)
            }
        } else if status != errSecSuccess {
            throw SessionStoreError.keychain(status)
        }
    }

    func clear() throws {
        let status = SecItemDelete(baseQuery as CFDictionary)
        guard status == errSecSuccess || status == errSecItemNotFound else {
            throw SessionStoreError.keychain(status)
        }
    }

    private var baseQuery: [String: Any] {
        [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
        ]
    }
}

/// Deterministic session persistence for tests and previews.
actor InMemorySessionStore: SessionStore {
    private var session: AuthSession?

    init(session: AuthSession? = nil) {
        self.session = session
    }

    func load() -> AuthSession? {
        session
    }

    func save(_ session: AuthSession) {
        self.session = session
    }

    func clear() {
        session = nil
    }
}

enum SessionStoreError: Error, Sendable {
    case keychain(OSStatus)
}
