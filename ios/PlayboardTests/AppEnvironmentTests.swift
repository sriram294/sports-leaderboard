import Foundation
import Testing
@testable import Playboard

@Suite("App environment")
struct AppEnvironmentTests {
    @Test("Preview storage is deterministic")
    func previewStorageRoundTrip() async {
        let environment = AppEnvironment.preview()
        let value = Data("playboard".utf8)

        await environment.keyValueStore.set(value, forKey: "fixture")
        let storedValue = await environment.keyValueStore.data(forKey: "fixture")

        #expect(storedValue == value)
    }
}
