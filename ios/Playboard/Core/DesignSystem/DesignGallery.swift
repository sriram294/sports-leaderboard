import SwiftUI

/// Debug-only catalogue of S00 tokens, assets, and reusable states.
struct DesignGallery: View {
    @Environment(\.playboardPalette) private var palette

    private let semanticColors: [(String, KeyPath<PlayboardPalette, Color>)] = [
        ("Brand", \.brand), ("Win", \.statWin), ("Loss", \.statLoss),
        ("Mid", \.winRateMid), ("Low", \.winRateLow), ("Surface", \.surface)
    ]

    var body: some View {
        PlayboardBackground {
            ScrollView {
                LazyVStack(alignment: .leading, spacing: PlayboardSpacing.extraLarge) {
                    galleryHeader
                    typography
                    paletteSection
                    assets
                    controls
                    states
                }
                .padding(.horizontal, PlayboardSpacing.large)
                .padding(.vertical, PlayboardSpacing.extraLarge)
            }
            .accessibilityIdentifier("design-gallery")
        }
    }

    private var galleryHeader: some View {
        VStack(alignment: .leading, spacing: PlayboardSpacing.small) {
            AppWordmark(logoHeight: 42, fontSize: 34)
            Text("DESIGN FOUNDATION · S00")
                .font(PlayboardTypography.eyebrow())
                .tracking(2)
                .foregroundStyle(palette.textMuted)
            Text("Built for the court.")
                .font(PlayboardTypography.display())
                .foregroundStyle(palette.textPrimary)
                .minimumScaleFactor(0.8)
        }
    }

    private var typography: some View {
        gallerySection("Typography") {
            Text("40  ·  27  ·  11").font(PlayboardTypography.display())
            Text("Monthly rankings").font(PlayboardTypography.title())
            Text("Manrope keeps dense scores readable and scales with your preferred text size.")
                .font(PlayboardTypography.body())
            Text("PLAYED  WON  LOST  RATE").font(PlayboardTypography.eyebrow()).tracking(2)
        }
    }

    private var paletteSection: some View {
        gallerySection("Semantic colors") {
            LazyVGrid(columns: [GridItem(.adaptive(minimum: 82))], spacing: PlayboardSpacing.small) {
                ForEach(semanticColors.indices, id: \.self) { index in
                    let item = semanticColors[index]
                    VStack(spacing: PlayboardSpacing.small) {
                        RoundedRectangle(cornerRadius: 12, style: .continuous)
                            .fill(palette[keyPath: item.1])
                            .frame(height: 48)
                            .overlay(Image(systemName: "checkmark").foregroundStyle(item.0 == "Brand" ? palette.onBrand : .white))
                        Text(item.0).font(PlayboardTypography.label()).foregroundStyle(palette.textMuted)
                    }
                }
            }
        }
    }

    private var assets: some View {
        gallerySection("Assets") {
            HStack(spacing: PlayboardSpacing.extraLarge) {
                Image("Crown").resizable().scaledToFit().frame(width: 36, height: 36).accessibilityLabel("Monthly crown")
                Image("PodiumCrown").resizable().scaledToFit().frame(width: 54, height: 54).accessibilityLabel("Champion crown")
                Text("Awards").font(PlayboardTypography.label())
            }
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: PlayboardSpacing.medium) {
                    ForEach(0..<16, id: \.self) { index in
                        PlayerAvatar(displayName: "Player \(index + 1)", avatarID: "avatar\(index)", color: palette.brand)
                    }
                }
                .padding(.vertical, 2)
            }
            .accessibilityLabel("Sixteen default avatars")
        }
    }

    private var controls: some View {
        gallerySection("Controls") {
            PlayboardPrimaryButton("Start a match", systemImage: "plus") {}
            HStack {
                Label("This Month", systemImage: "calendar")
                Spacer()
                Label("All Time", systemImage: "infinity")
            }
            .font(PlayboardTypography.label())
            .foregroundStyle(palette.textPrimary)
            .frame(minHeight: 44)
        }
    }

    private var states: some View {
        gallerySection("Reusable states") {
            PlayboardLoadingView(message: "Loading rankings")
            Divider().overlay(palette.textMuted.opacity(0.3))
            PlayboardEmptyView(title: "No matches yet", message: "Record the first match to start this leaderboard.")
            Divider().overlay(palette.textMuted.opacity(0.3))
            PlayboardErrorView(message: "Check your connection, then try again.") {}
        }
    }

    private func gallerySection<Content: View>(
        _ title: String,
        @ViewBuilder content: () -> Content
    ) -> some View {
        VStack(alignment: .leading, spacing: PlayboardSpacing.medium) {
            Text(title.uppercased())
                .font(PlayboardTypography.eyebrow())
                .tracking(2)
                .foregroundStyle(palette.textMuted)
            PlayboardCard {
                VStack(alignment: .leading, spacing: PlayboardSpacing.large, content: content)
                    .foregroundStyle(palette.textPrimary)
            }
        }
    }
}

#Preview("Gallery · Dark") {
    PlayboardTheme { DesignGallery() }.preferredColorScheme(.dark)
}

#Preview("Gallery · Light") {
    PlayboardTheme { DesignGallery() }.preferredColorScheme(.light)
}
