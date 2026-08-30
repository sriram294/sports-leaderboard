import SwiftUI

/// Chooses the S00 foundation surface without requiring provider configuration.
struct RootView: View {
    let environment: AppEnvironment

    private var showsDesignGallery: Bool {
        #if DEBUG
        ProcessInfo.processInfo.arguments.contains("-design-gallery")
        #else
        false
        #endif
    }

    var body: some View {
        if showsDesignGallery {
            DesignGallery()
        } else {
            FoundationScreen()
        }
    }
}

#Preview("Foundation") {
    RootView(environment: .preview())
}
