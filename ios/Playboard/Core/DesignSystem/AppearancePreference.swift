import SwiftUI

/// The user's appearance choice from Settings. Playboard defaults to dark, like Android.
enum AppearancePreference: String, CaseIterable, Identifiable, Sendable {
    case dark
    case light
    case system

    /// `UserDefaults` key backing the `@AppStorage` preference.
    static let storageKey = "playboard.appearance"

    var id: String { rawValue }

    var title: String {
        switch self {
        case .dark: "Dark"
        case .light: "Light"
        case .system: "Match system"
        }
    }

    /// The scheme to force, or nil to follow the system.
    var colorScheme: ColorScheme? {
        #if DEBUG
        // UI tests choose light/dark through -AppleInterfaceStyle, so they must follow the system.
        if ProcessInfo.processInfo.arguments.contains("-AppleInterfaceStyle") { return nil }
        #endif
        return switch self {
        case .dark: .dark
        case .light: .light
        case .system: nil
        }
    }
}
