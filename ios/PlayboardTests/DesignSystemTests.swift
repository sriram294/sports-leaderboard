import SwiftUI
import Testing
import UIKit
@testable import Playboard

@Suite("Design system")
struct DesignSystemTests {
    @Test("Light and dark palettes stay distinct")
    func palettesAreDistinct() {
        #expect(PlayboardPalette.light != PlayboardPalette.dark)
    }

    @Test("Default avatar catalogue is complete")
    func avatarAssetsExist() {
        for index in 0..<16 {
            #expect(UIImage(named: "avatar\(index)") != nil)
        }
    }

    @Test("Bundled typography roles register")
    func fontsRegister() {
        let names = [
            "Manrope-Regular",
            "Manrope-Medium",
            "Manrope-SemiBold",
            "Manrope-Bold",
            "Manrope-ExtraBold",
            "PaytoneOne-Regular",
        ]

        for name in names {
            #expect(UIFont(name: name, size: 16) != nil, "Missing bundled font: \(name)")
        }
    }
}
