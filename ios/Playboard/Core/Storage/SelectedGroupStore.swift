import Foundation

/// Persists the selected group independently of any feature screen.
protocol SelectedGroupStore: Sendable {
    func selectedGroupID() async -> String?
    func setSelectedGroupID(_ id: String?) async
}

/// Key-value-backed selected-group preference.
actor KeyValueSelectedGroupStore: SelectedGroupStore {
    private let store: any KeyValueStore
    private let key = "selected-group-id"

    init(store: any KeyValueStore) {
        self.store = store
    }

    func selectedGroupID() async -> String? {
        guard let data = await store.data(forKey: key) else { return nil }
        return String(data: data, encoding: .utf8)
    }

    func setSelectedGroupID(_ id: String?) async {
        await store.set(id.map { Data($0.utf8) }, forKey: key)
    }
}
