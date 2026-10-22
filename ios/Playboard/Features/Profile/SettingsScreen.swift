import SwiftUI

/// Account, appearance, and app information, presented from the Profile gear.
struct SettingsScreen: View {
    let user: AuthenticatedUser
    let signOut: () -> Void
    let deleteAccount: () async throws -> Void
    @AppStorage(AppearancePreference.storageKey) private var appearance = AppearancePreference.dark
    @Environment(\.dismiss) private var dismiss
    @Environment(\.playboardPalette) private var palette
    @State private var showsDeleteAccount = false

    var body: some View {
        NavigationStack {
            Form {
                Section("Account") {
                    VStack(alignment: .leading, spacing: PlayboardSpacing.extraSmall) {
                        Text(providerLine).font(PlayboardTypography.label())
                        Text(user.email).font(PlayboardTypography.body()).foregroundStyle(palette.textMuted)
                    }
                    .accessibilityElement(children: .combine)
                    Button(role: .destructive) {
                        dismiss()
                        signOut()
                    } label: {
                        Label("Sign out", systemImage: "rectangle.portrait.and.arrow.right")
                    }
                    .accessibilityIdentifier("sign-out-button")
                    Button(role: .destructive) { showsDeleteAccount = true } label: {
                        Label("Delete account", systemImage: "person.crop.circle.badge.xmark")
                    }
                    .accessibilityIdentifier("delete-account-button")
                }
                Section("Appearance") {
                    Picker("Theme", selection: $appearance) {
                        ForEach(AppearancePreference.allCases) { Text($0.title).tag($0) }
                    }
                    .accessibilityIdentifier("appearance-picker")
                }
                Section {
                    Text("Playboard v\(versionName)")
                    Text("Made by Sriram Elangovan")
                    Text("Avatars: “3D Web3 Avatars” by Koncepted (Figma Community)")
                }
                .font(PlayboardTypography.body())
                .foregroundStyle(palette.textMuted)
            }
            .navigationTitle("Settings")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Done") { dismiss() } } }
            .sheet(isPresented: $showsDeleteAccount) {
                AccountDeletionSheet(deleteAccount: deleteAccount, onDeleted: { dismiss() })
            }
        }
        .accessibilityIdentifier("settings-screen")
    }

    private var providerLine: String {
        switch (user.authProviders.contains(.google), user.authProviders.contains(.apple)) {
        case (true, true): "Signed in with Google and Apple"
        case (false, true): "Signed in with Apple"
        case (true, false): "Signed in with Google"
        case (false, false): "Signed in"
        }
    }

    private var versionName: String {
        Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "—"
    }
}
