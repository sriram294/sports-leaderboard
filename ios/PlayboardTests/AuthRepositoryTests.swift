import Foundation
import Testing
@testable import Playboard

@Suite("Authentication repository")
struct AuthRepositoryTests {
    private let now = Date(timeIntervalSince1970: 1_788_173_200)

    @Test("A valid stored session restores without network traffic")
    func restoresValidSession() async throws {
        let stored = session(expiresAt: now.addingTimeInterval(600))
        let store = InMemorySessionStore(session: stored)
        let client = RecordingAPIClient(responses: [])
        let repository = repository(client: client, store: store)

        let restored = try await repository.restoreSession()

        #expect(restored == stored)
        let requestCount = await client.requestCount
        #expect(requestCount == 0)
    }

    @Test("An expired access token rotates through refresh and keeps the user")
    func refreshesExpiredSession() async throws {
        let stored = session(expiresAt: now.addingTimeInterval(-1))
        let store = InMemorySessionStore(session: stored)
        let client = RecordingAPIClient(responses: [jsonResponse("""
        {"accessToken":"access-2","refreshToken":"refresh-2","expiresIn":900}
        """)])
        let repository = repository(client: client, store: store)

        let restoredValue = try await repository.restoreSession()
        let restored = try #require(restoredValue)

        #expect(restored.accessToken == "access-2")
        #expect(restored.refreshToken == "refresh-2")
        #expect(restored.user == stored.user)
        let requestCount = await client.requestCount
        let lastPath = await client.lastPath
        #expect(requestCount == 1)
        #expect(lastPath == "/api/v1/auth/refresh")
    }

    @Test("Concurrent refresh requests share one rotation")
    func coalescesRefreshRace() async throws {
        let stored = session(expiresAt: now.addingTimeInterval(-1))
        let store = InMemorySessionStore(session: stored)
        let client = RecordingAPIClient(
            responses: [jsonResponse("""
            {"accessToken":"access-2","refreshToken":"refresh-2","expiresIn":900}
            """)],
            delay: .milliseconds(50)
        )
        let repository = repository(client: client, store: store)

        async let first = repository.restoreSession()
        async let second = repository.restoreSession()
        let values = try await (first, second)

        #expect(values.0?.accessToken == "access-2")
        #expect(values.1?.accessToken == "access-2")
        let requestCount = await client.requestCount
        #expect(requestCount == 1)
    }

    @Test("Google exchange persists the complete session")
    func persistsGoogleSignIn() async throws {
        let store = InMemorySessionStore()
        let client = RecordingAPIClient(responses: [jsonResponse(signInJSON)])
        let repository = repository(client: client, store: store)

        let signedIn = try await repository.signInWithGoogle(
            ProviderCredential(identityToken: "provider-token", givenName: nil, familyName: nil)
        )

        #expect(signedIn.user.displayName == "Priya")
        #expect(signedIn.user.authProviders == [.google, .apple])
        let persisted = try await store.load()
        let lastPath = await client.lastPath
        let lastBody = await client.lastBody
        #expect(persisted == signedIn)
        #expect(lastPath == "/api/v1/auth/google")
        #expect(lastBody?.contains("provider-token") == true)
    }

    private func repository(client: RecordingAPIClient, store: InMemorySessionStore) -> LiveAuthRepository {
        LiveAuthRepository(
            apiClient: client,
            sessionStore: store,
            baseURL: URL(string: "https://example.test/api/v1")!,
            clock: FixedClock(now: now)
        )
    }

    private func session(expiresAt: Date) -> AuthSession {
        AuthSession(
            accessToken: "access-1",
            refreshToken: "refresh-1",
            accessTokenExpiresAt: expiresAt,
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

    private var signInJSON: String {
        """
        {
          "accessToken":"access-1",
          "refreshToken":"refresh-1",
          "expiresIn":900,
          "user":{
            "id":"user-1",
            "displayName":"Priya",
            "email":"priya@example.com",
            "photoUrl":null,
            "avatarId":"avatar1",
            "avatarColor":"#9ADE28",
            "authProviders":["google","apple"]
          }
        }
        """
    }
}

private struct FixedClock: PlayboardClock {
    let now: Date
}

private actor RecordingAPIClient: APIClient {
    private var responses: [APIResponse]
    private let delay: Duration?
    private(set) var requests: [URLRequest] = []

    init(responses: [APIResponse], delay: Duration? = nil) {
        self.responses = responses
        self.delay = delay
    }

    var requestCount: Int { requests.count }
    var lastPath: String? { requests.last?.url?.path }
    var lastBody: String? { requests.last?.httpBody.flatMap { String(data: $0, encoding: .utf8) } }

    func response(for request: URLRequest) async throws -> APIResponse {
        requests.append(request)
        if let delay {
            try await Task.sleep(for: delay)
        }
        guard !responses.isEmpty else {
            throw APIClientError.invalidResponse
        }
        return responses.removeFirst()
    }
}

private func jsonResponse(_ json: String, statusCode: Int = 200) -> APIResponse {
    APIResponse(data: Data(json.utf8), statusCode: statusCode)
}
