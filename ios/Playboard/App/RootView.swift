import SwiftUI

/// Chooses the S00 foundation surface without requiring provider configuration.
struct RootView: View {
    let environment: AppEnvironment
    @StateObject private var sessionViewModel: SessionViewModel

    init(environment: AppEnvironment) {
        self.environment = environment
        _sessionViewModel = StateObject(wrappedValue: SessionViewModel(
            repository: environment.authRepository,
            googleProvider: environment.googleAuthProvider
        ))
    }

    private var showsDesignGallery: Bool {
        #if DEBUG
        ProcessInfo.processInfo.arguments.contains("-design-gallery")
        #else
        false
        #endif
    }

    var body: some View {
        Group {
            if showsDesignGallery {
                DesignGallery()
            } else {
                sessionContent
            }
        }
        .task { await sessionViewModel.restoreIfNeeded() }
    }

    @ViewBuilder
    private var sessionContent: some View {
        switch sessionViewModel.state {
        case .restoring:
            PlayboardBackground {
                PlayboardLoadingView(message: "Restoring your session")
                    .padding(PlayboardSpacing.extraLarge)
            }
        case .signedOut:
            LoginScreen(environment: environment, onAuthenticated: sessionViewModel.accept)
        case .signedIn(let session):
            SignedInAccountScreen(session: session) {
                Task { @MainActor in await sessionViewModel.signOut() }
            }
        case .recovery(let message):
            PlayboardBackground {
                PlayboardErrorView(message: message) {
                    Task { @MainActor in await sessionViewModel.retryRestore() }
                }
                .padding(PlayboardSpacing.extraLarge)
            }
        }
    }
}

#Preview("Foundation") {
    RootView(environment: .preview())
}
