import Foundation

/// The application's explicit dependency composition root.
struct AppEnvironment: Sendable {
    let apiClient: any APIClient
    let keyValueStore: any KeyValueStore

    /// Production dependencies are created exactly once for the app process.
    static let live = AppEnvironment(
        apiClient: URLSessionAPIClient(),
        keyValueStore: InMemoryKeyValueStore()
    )

    /// A deterministic environment for previews and tests.
    static func preview() -> AppEnvironment {
        AppEnvironment(
            apiClient: StubAPIClient(data: Data()),
            keyValueStore: InMemoryKeyValueStore()
        )
    }
}
