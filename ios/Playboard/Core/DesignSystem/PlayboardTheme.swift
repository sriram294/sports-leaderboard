import SwiftUI

/// Semantic light/dark palette shared by every Playboard surface.
struct PlayboardPalette: Equatable, Sendable {
    let background: Color
    let surface: Color
    let glowWarm: Color
    let glowCool: Color
    let textPrimary: Color
    let textMuted: Color
    let brand: Color
    let onBrand: Color
    let statWin: Color
    let statLoss: Color
    let winRateMid: Color
    let winRateLow: Color

    static let dark = PlayboardPalette(
        background: Color(hex: 0x0A0A0A),
        surface: Color(hex: 0x141414),
        glowWarm: Color(hex: 0x9ADE28).opacity(0.15),
        glowCool: Color(hex: 0x5B8CFF).opacity(0.10),
        textPrimary: Color(hex: 0xF5F5F5),
        textMuted: Color(hex: 0x9E9E9E),
        brand: Color(hex: 0x9ADE28),
        onBrand: Color(hex: 0x0A0A0A),
        statWin: Color(hex: 0x4ADE80),
        statLoss: Color(hex: 0xF87171),
        winRateMid: Color(hex: 0xFACC15),
        winRateLow: Color(hex: 0x60A5FA)
    )

    static let light = PlayboardPalette(
        background: Color(hex: 0xFAFAFA),
        surface: .white,
        glowWarm: Color(hex: 0x9ADE28).opacity(0.17),
        glowCool: Color(hex: 0x2563EB).opacity(0.09),
        textPrimary: Color(hex: 0x1A1A1A),
        textMuted: Color(hex: 0x6B6B6B),
        brand: Color(hex: 0x4E8C0A),
        onBrand: .white,
        statWin: Color(hex: 0x16A34A),
        statLoss: Color(hex: 0xDC2626),
        winRateMid: Color(hex: 0xCA8A04),
        winRateLow: Color(hex: 0x2563EB)
    )
}

extension Color {
    /// Creates an opaque sRGB color from a six-digit RGB integer.
    init(hex: UInt32) {
        self.init(
            .sRGB,
            red: Double((hex >> 16) & 0xFF) / 255,
            green: Double((hex >> 8) & 0xFF) / 255,
            blue: Double(hex & 0xFF) / 255,
            opacity: 1
        )
    }
}

/// Playboard's compact spacing rhythm.
enum PlayboardSpacing {
    static let extraSmall: CGFloat = 4
    static let small: CGFloat = 8
    static let medium: CGFloat = 12
    static let large: CGFloat = 16
    static let extraLarge: CGFloat = 24
    static let section: CGFloat = 32
}

private struct PlayboardPaletteKey: EnvironmentKey {
    static let defaultValue = PlayboardPalette.dark
}

extension EnvironmentValues {
    var playboardPalette: PlayboardPalette {
        get { self[PlayboardPaletteKey.self] }
        set { self[PlayboardPaletteKey.self] = newValue }
    }
}

/// Supplies semantic colors based on the current appearance.
struct PlayboardTheme<Content: View>: View {
    @Environment(\.colorScheme) private var colorScheme
    @ViewBuilder private let content: () -> Content

    init(@ViewBuilder content: @escaping () -> Content) {
        self.content = content
    }

    var body: some View {
        let palette = colorScheme == .dark ? PlayboardPalette.dark : .light
        content()
            .environment(\.playboardPalette, palette)
            .tint(palette.brand)
    }
}
