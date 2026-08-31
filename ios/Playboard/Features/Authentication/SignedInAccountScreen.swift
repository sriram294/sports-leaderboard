import SwiftUI

/// S01 authenticated destination used until the S02 app shell owns routing.
struct SignedInAccountScreen: View {
    @Environment(\.playboardPalette) private var palette
    let session: AuthSession
    let signOut: () -> Void

    var body: some View {
        PlayboardBackground {
            VStack(spacing: PlayboardSpacing.section) {
                AppWordmark()
                Spacer()
                PlayboardCard {
                    VStack(alignment: .leading, spacing: PlayboardSpacing.large) {
                        Label("Signed in", systemImage: "checkmark.shield")
                            .font(PlayboardTypography.title())
                            .foregroundStyle(palette.textPrimary)
                        Text(session.user.displayName)
                            .font(PlayboardTypography.title())
                            .foregroundStyle(palette.textPrimary)
                        Text(session.user.email)
                            .font(PlayboardTypography.body())
                            .foregroundStyle(palette.textMuted)
                        Text(session.user.authProviders.map(\.rawValue.capitalized).joined(separator: " · "))
                            .font(PlayboardTypography.eyebrow())
                            .foregroundStyle(palette.textMuted)
                        Button(role: .destructive, action: signOut) {
                            Label("Sign out", systemImage: "rectangle.portrait.and.arrow.right")
                                .font(PlayboardTypography.label())
                                .frame(maxWidth: .infinity, minHeight: 44)
                        }
                        .buttonStyle(.bordered)
                        .accessibilityIdentifier("sign-out-button")
                    }
                }
                Spacer()
                Text("Groups and navigation arrive in S02.")
                    .font(PlayboardTypography.eyebrow())
                    .foregroundStyle(palette.textMuted)
            }
            .padding(PlayboardSpacing.extraLarge)
            .frame(maxWidth: 560)
            .accessibilityIdentifier("signed-in-screen")
        }
    }
}
