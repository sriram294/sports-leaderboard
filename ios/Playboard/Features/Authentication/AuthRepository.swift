import Foundation

/// Provider credential passed from a native adapter to the Playboard API.
struct ProviderCredential: Equatable, Sendable {
    let identityToken: String
    let givenName: String?
    let familyName: String?
}

/// Authentication and durable-session boundary used by presentation code.
protocol AuthRepository: Sendable {
    func restoreSession() async throws -> AuthSession?
    func signInWithGoogle(_ credential: ProviderCredential) async throws -> AuthSession
    func signInWithApple(_ credential: ProviderCredential) async throws -> AuthSession
    func refreshSession() async throws -> AuthSession
    func logout() async
}

/// Stable authentication failures with user-facing recovery guidance.
enum AuthError: Error, Equatable, Sendable {
    case notConfigured(String)
    case cancelled
    case offline
    case expiredSession
    case rejected(String)
    case invalidResponse

    var message: String {
        switch self {
        case .notConfigured(let provider):
            "(provider) sign-in is not configured for this build."
        case .cancelled:
            "Sign-in was cancelled."
        case .offline:
            "You're offline. Check your connection and try again."
        case .expiredSession:
            "Your session expired. Sign in again to continue."
        case .rejected(let message):
            message
        case .invalidResponse:
            "Playboard returned an unexpected response. Try again."
        }
    }
}

/// Actor-isolated API implementation that coalesces simultaneous refreshes.
actor LiveAuthRepository: AuthRepository {
    private let apiClient: any APIClient
    private let sessionStore: any SessionStore
    private let baseURL: URL?
    private let clock: any PlayboardClock
    private let encoder = JSONEncoder()
    private let decoder = JSONDecoder()
    private var currentSession: AuthSession?
    private var refreshTask: Task<AuthSession, Error>?

    init(
        apiClient: any APIClient,
        sessionStore: any SessionStore,
        baseURL: URL?,
        clock: any PlayboardClock
    ) {
        self.apiClient = apiClient
        self.sessionStore = sessionStore
        self.baseURL = baseURL
        self.clock = clock
    }

    func restoreSession() async throws -> AuthSession? {
        guard let stored = try await sessionStore.load() else {
            return nil
        }
        currentSession = stored
        if stored.accessTokenExpiresAt > clock.now.addingTimeInterval(30) {
            return stored
        }
        do {
            return try await refresh(stored)
        } catch {
            try? await sessionStore.clear()
            currentSession = nil
            throw map(error)
        }
    }

    func signInWithGoogle(_ credential: ProviderCredential) async throws -> AuthSession {
        try await signIn(path: "auth/google", body: GoogleRequest(idToken: credential.identityToken))
    }

    func signInWithApple(_ credential: ProviderCredential) async throws -> AuthSession {
        try await signIn(
            path: "auth/apple",
            body: AppleRequest(
                identityToken: credential.identityToken,
                givenName: credential.givenName,
                familyName: credential.familyName
            )
        )
    }

    func refreshSession() async throws -> AuthSession {
        let session = currentSession ?? (try await sessionStore.load())
        guard let session else {
            throw AuthError.expiredSession
        }
        return try await refresh(session)
    }

    func logout() async {
        let session = currentSession ?? (try? await sessionStore.load())
        if let session, let baseURL {
            var request = request(path: "auth/logout", baseURL: baseURL)
            request.setValue("Bearer (session.accessToken)", forHTTPHeaderField: "Authorization")
            request.httpBody = try? encoder.encode(RefreshRequest(refreshToken: session.refreshToken))
            _ = try? await apiClient.response(for: request)
        }
        try? await sessionStore.clear()
        currentSession = nil
    }

    private func signIn<Body: Encodable>(path: String, body: Body) async throws -> AuthSession {
        guard let baseURL else {
            throw AuthError.notConfigured("Playboard API")
        }
        var request = request(path: path, baseURL: baseURL)
        request.httpBody = try encoder.encode(body)
        let response: SignInResponse = try await send(request)
        let session = AuthSession(
            accessToken: response.accessToken,
            refreshToken: response.refreshToken,
            accessTokenExpiresAt: clock.now.addingTimeInterval(TimeInterval(response.expiresIn)),
            user: response.user
        )
        try await sessionStore.save(session)
        currentSession = session
        return session
    }

    private func refresh(_ session: AuthSession) async throws -> AuthSession {
        if let refreshTask {
            return try await refreshTask.value
        }
        let task = Task { try await performRefresh(session) }
        refreshTask = task
        do {
            let refreshed = try await task.value
            refreshTask = nil
            return refreshed
        } catch {
            refreshTask = nil
            throw map(error)
        }
    }

    private func performRefresh(_ session: AuthSession) async throws -> AuthSession {
        guard let baseURL else {
            throw AuthError.notConfigured("Playboard API")
        }
        var request = request(path: "auth/refresh", baseURL: baseURL)
        request.httpBody = try encoder.encode(RefreshRequest(refreshToken: session.refreshToken))
        let response: RefreshResponse = try await send(request)
        let refreshed = AuthSession(
            accessToken: response.accessToken,
            refreshToken: response.refreshToken,
            accessTokenExpiresAt: clock.now.addingTimeInterval(TimeInterval(response.expiresIn)),
            user: session.user
        )
        try await sessionStore.save(refreshed)
        currentSession = refreshed
        return refreshed
    }

    private func request(path: String, baseURL: URL) -> URLRequest {
        let url = path.split(separator: "/").reduce(baseURL) { partialURL, component in
            partialURL.appendingPathComponent(String(component))
        }
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.timeoutInterval = 20
        return request
    }

    private func send<Response: Decodable>(_ request: URLRequest) async throws -> Response {
        do {
            let response = try await apiClient.response(for: request)
            guard (200..<300).contains(response.statusCode) else {
                if response.statusCode == 401 {
                    throw AuthError.expiredSession
                }
                let problem = try? decoder.decode(ProblemResponse.self, from: response.data)
                throw AuthError.rejected(problem?.detail ?? "Sign-in could not be completed.")
            }
            return try decoder.decode(Response.self, from: response.data)
        } catch let error as AuthError {
            throw error
        } catch let error as URLError
            where error.code == .notConnectedToInternet || error.code == .networkConnectionLost {
            throw AuthError.offline
        } catch is DecodingError {
            throw AuthError.invalidResponse
        } catch {
            throw map(error)
        }
    }

    private func map(_ error: Error) -> AuthError {
        if let authError = error as? AuthError {
            return authError
        }
        if let urlError = error as? URLError,
           urlError.code == .notConnectedToInternet || urlError.code == .networkConnectionLost {
            return .offline
        }
        return .invalidResponse
    }
}

private struct GoogleRequest: Encodable {
    let idToken: String
}

private struct AppleRequest: Encodable {
    let identityToken: String
    let givenName: String?
    let familyName: String?
}

private struct RefreshRequest: Encodable {
    let refreshToken: String
}

private struct SignInResponse: Decodable {
    let accessToken: String
    let refreshToken: String
    let expiresIn: Int
    let user: AuthenticatedUser
}

private struct RefreshResponse: Decodable {
    let accessToken: String
    let refreshToken: String
    let expiresIn: Int
}

private struct ProblemResponse: Decodable {
    let detail: String?
}
