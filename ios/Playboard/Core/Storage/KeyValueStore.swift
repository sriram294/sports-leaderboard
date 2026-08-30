import Foundation

/// Asynchronous storage boundary for later repositories.
protocol KeyValueStore: Sendable {
    func data(forKey key: String) async -> Data?
    func set(_ data: Data?, forKey key: String) async
}

/// Deterministic process-local storage used until secure persistence is introduced by its owning slice.
actor InMemoryKeyValueStore: KeyValueStore {
    private var values: [String: Data] = [:]

    func data(forKey key: String) -> Data? {
        values[key]
    }

    func set(_ data: Data?, forKey key: String) {
        values[key] = data
    }
}
