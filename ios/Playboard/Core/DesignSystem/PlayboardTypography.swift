import SwiftUI

/// Bundled Playboard type roles with Dynamic Type scaling.
enum PlayboardTypography {
    static func display() -> Font {
        .custom("Manrope-ExtraBold", size: 40, relativeTo: .largeTitle)
    }

    static func title() -> Font {
        .custom("Manrope-Bold", size: 22, relativeTo: .title2)
    }

    static func body() -> Font {
        .custom("Manrope-Regular", size: 16, relativeTo: .body)
    }

    static func label() -> Font {
        .custom("Manrope-SemiBold", size: 13, relativeTo: .caption)
    }

    static func eyebrow() -> Font {
        .custom("Manrope-Medium", size: 11, relativeTo: .caption2)
    }

    static func wordmark(size: CGFloat) -> Font {
        .custom("PaytoneOne-Regular", size: size, relativeTo: .title)
    }
}
