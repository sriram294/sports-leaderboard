import SwiftUI

/// Native Playboard application entry point.
@main
struct PlayboardApp: App {
    private let environment = AppEnvironment.live

    var body: some Scene {
        WindowGroup {
            PlayboardTheme {
                RootView(environment: environment)
            }
        }
    }
}
