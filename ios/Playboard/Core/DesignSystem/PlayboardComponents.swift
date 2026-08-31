import SwiftUI

/// Racket-as-P Playboard wordmark.
struct AppWordmark: View {
    @Environment(\.playboardPalette) private var palette
    var logoHeight: CGFloat = 34
    var fontSize: CGFloat = 27

    var body: some View {
        HStack(alignment: .center, spacing: logoHeight * 0.07) {
            Image("WordmarkRacket")
                .resizable()
                .scaledToFit()
                .frame(height: logoHeight)
            Text("layboard")
                .font(PlayboardTypography.wordmark(size: fontSize))
                .foregroundStyle(palette.brand)
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Playboard")
    }
}

/// Primary brand action with a minimum accessible hit target.
struct PlayboardPrimaryButton: View {
    @Environment(\.playboardPalette) private var palette
    let title: String
    let systemImage: String?
    let action: () -> Void

    init(_ title: String, systemImage: String? = nil, action: @escaping () -> Void) {
        self.title = title
        self.systemImage = systemImage
        self.action = action
    }

    var body: some View {
        Button(action: action) {
            Label(title, systemImage: systemImage ?? "arrow.right")
                .font(PlayboardTypography.label())
                .frame(maxWidth: .infinity, minHeight: 44)
                .foregroundStyle(palette.onBrand)
                .background(palette.brand, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
        }
        .buttonStyle(.plain)
    }
}

/// Elevated content surface that remains distinct above the ambient glow.
struct PlayboardCard<Content: View>: View {
    @Environment(\.playboardPalette) private var palette
    @ViewBuilder private let content: () -> Content

    init(@ViewBuilder content: @escaping () -> Content) {
        self.content = content
    }

    var body: some View {
        content()
            .padding(PlayboardSpacing.large)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(palette.surface.opacity(0.92), in: RoundedRectangle(cornerRadius: 20, style: .continuous))
            .overlay {
                RoundedRectangle(cornerRadius: 20, style: .continuous)
                    .stroke(palette.textMuted.opacity(0.18), lineWidth: 1)
            }
    }
}

/// Bundled avatar with a semantic fallback initial and persistent player-color ring.
struct PlayerAvatar: View {
    let displayName: String
    let avatarID: String?
    var photoURL: String? = nil
    let color: Color
    var size: CGFloat = 48

    var body: some View {
        ZStack {
            Circle().fill(color.opacity(0.16))
            Text(String(displayName.prefix(1)).uppercased())
                .font(.custom("Manrope-Bold", size: size * 0.4, relativeTo: .body))
                .foregroundStyle(color)
            if let avatarID {
                Image(avatarID)
                    .resizable()
                    .scaledToFill()
                    .frame(width: size, height: size)
                    .clipShape(Circle())
            }
            if let photoURL, let url = URL(string: photoURL) {
                AsyncImage(url: url) { phase in
                    if case .success(let image) = phase {
                        image.resizable().scaledToFill()
                    }
                }
                .frame(width: size, height: size)
                .clipShape(Circle())
            }
        }
        .frame(width: size, height: size)
        .overlay(Circle().stroke(color, lineWidth: max(2, size / 18)))
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Avatar for \(displayName)")
    }
}

/// Standard progress state with spoken context.
struct PlayboardLoadingView: View {
    @Environment(\.playboardPalette) private var palette
    let message: String

    var body: some View {
        VStack(spacing: PlayboardSpacing.medium) {
            ProgressView().controlSize(.large)
            Text(message).font(PlayboardTypography.body()).foregroundStyle(palette.textMuted)
        }
        .frame(maxWidth: .infinity, minHeight: 120)
        .accessibilityElement(children: .combine)
        .accessibilityLabel(message)
    }
}

/// Actionable empty state with a symbol so meaning never depends on color.
struct PlayboardEmptyView: View {
    let title: String
    let message: String

    var body: some View {
        PlayboardStateView(symbol: "sportscourt", title: title, message: message)
    }
}

/// Actionable error state with a non-color warning symbol.
struct PlayboardErrorView: View {
    let message: String
    let retry: () -> Void

    var body: some View {
        VStack(spacing: PlayboardSpacing.large) {
            PlayboardStateView(symbol: "exclamationmark.triangle", title: "Couldn't load Playboard", message: message)
            PlayboardPrimaryButton("Try again", systemImage: "arrow.clockwise", action: retry)
        }
    }
}

private struct PlayboardStateView: View {
    @Environment(\.playboardPalette) private var palette
    let symbol: String
    let title: String
    let message: String

    var body: some View {
        VStack(spacing: PlayboardSpacing.small) {
            Image(systemName: symbol)
                .font(.system(size: 28, weight: .semibold))
                .foregroundStyle(palette.brand)
            Text(title).font(PlayboardTypography.title()).foregroundStyle(palette.textPrimary)
            Text(message)
                .font(PlayboardTypography.body())
                .foregroundStyle(palette.textMuted)
                .multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity, minHeight: 120)
    }
}
