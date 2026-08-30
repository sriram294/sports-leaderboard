import SwiftUI

/// Two-light ambient field used across Playboard without obscuring dense content.
struct PlayboardBackground<Content: View>: View {
    @Environment(\.playboardPalette) private var palette
    @ViewBuilder private let content: () -> Content

    init(@ViewBuilder content: @escaping () -> Content) {
        self.content = content
    }

    var body: some View {
        ZStack {
            palette.background
                .ignoresSafeArea()

            GeometryReader { proxy in
                Circle()
                    .fill(palette.glowWarm)
                    .frame(width: proxy.size.width * 1.15, height: proxy.size.width * 1.15)
                    .blur(radius: 70)
                    .position(x: proxy.size.width * 0.18, y: -proxy.size.height * 0.04)

                Circle()
                    .fill(palette.glowCool)
                    .frame(width: proxy.size.width * 0.95, height: proxy.size.width * 0.95)
                    .blur(radius: 70)
                    .position(x: proxy.size.width * 1.06, y: proxy.size.height * 0.84)
            }
            .accessibilityHidden(true)
            .allowsHitTesting(false)

            content()
        }
    }
}
