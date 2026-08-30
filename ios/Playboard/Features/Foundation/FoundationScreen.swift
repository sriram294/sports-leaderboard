import SwiftUI

/// Safe S00 launch surface shown before authentication exists.
struct FoundationScreen: View {
    @Environment(\.playboardPalette) private var palette

    var body: some View {
        PlayboardBackground {
            VStack(spacing: PlayboardSpacing.section) {
                Spacer()
                AppWordmark(logoHeight: 60, fontSize: 46)
                VStack(spacing: PlayboardSpacing.small) {
                    Text("The court is ready")
                        .font(PlayboardTypography.title())
                        .foregroundStyle(palette.textPrimary)
                    Text("Native iOS foundations are in place. Sign-in arrives in the next delivery slice.")
                        .font(PlayboardTypography.body())
                        .foregroundStyle(palette.textMuted)
                        .multilineTextAlignment(.center)
                }
                .padding(.horizontal, PlayboardSpacing.extraLarge)
                Spacer()
                Label("Foundation · S00", systemImage: "checkmark.seal")
                    .font(PlayboardTypography.label())
                    .foregroundStyle(palette.textMuted)
                    .padding(.bottom, PlayboardSpacing.extraLarge)
            }
        }
    }
}
