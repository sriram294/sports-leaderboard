import SwiftUI
import GoogleSignIn

/// Native Playboard application entry point.
@main
struct PlayboardApp: App {
    @UIApplicationDelegateAdaptor(PlayboardAppDelegate.self) private var appDelegate
    @AppStorage(AppearancePreference.storageKey) private var appearance = AppearancePreference.dark
    private let environment = AppEnvironment.live

    var body: some Scene {
        WindowGroup {
            PlayboardTheme {
                RootView(environment: environment)
            }
            .preferredColorScheme(appearance.colorScheme)
            .onOpenURL { url in
                GIDSignIn.sharedInstance.handle(url)
            }
        }
    }
}
