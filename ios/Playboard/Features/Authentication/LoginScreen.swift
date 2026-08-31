import AuthenticationServices
import SwiftUI

/// Native Google/Apple login with progress, cancellation, and actionable failures.
struct LoginScreen: View {
    @Environment(\.colorScheme) private var colorScheme
    @Environment(\.playboardPalette) private var palette
    @StateObject private var viewModel: LoginViewModel
    private let configuration: ProviderConfiguration

    init(environment: AppEnvironment, onAuthenticated: @escaping (AuthSession) -> Void) {
        configuration = environment.configuration
        _viewModel = StateObject(wrappedValue: LoginViewModel(
            repository: environment.authRepository,
            googleProvider: environment.googleAuthProvider,
            appleParser: environment.appleCredentialParser,
            onAuthenticated: onAuthenticated
        ))
    }

    var body: some View {
        PlayboardBackground {
            ScrollView {
                VStack(spacing: PlayboardSpacing.section) {
                    Spacer(minLength: 72)
                    AppWordmark(logoHeight: 64, fontSize: 48)
                    VStack(spacing: PlayboardSpacing.small) {
                        Text("Welcome to the court")
                            .font(PlayboardTypography.title())
                            .foregroundStyle(palette.textPrimary)
                        Text("Sign in to record matches and follow your group leaderboard.")
                            .font(PlayboardTypography.body())
                            .foregroundStyle(palette.textMuted)
                            .multilineTextAlignment(.center)
                    }

                    PlayboardCard {
                        VStack(spacing: PlayboardSpacing.large) {
                            if viewModel.state.activeProvider == .google {
                                providerProgress("Signing in with Google")
                            } else {
                                GoogleSignInButton(
                                    isEnabled: canUseGoogle,
                                    action: { Task { @MainActor in await viewModel.signInWithGoogle() } }
                                )
                                .frame(maxWidth: .infinity, minHeight: 50)
                                .accessibilityIdentifier("google-sign-in-button")
                                .accessibilityLabel("Sign in with Google")
                            }

                            if viewModel.state.activeProvider == .apple {
                                providerProgress("Signing in with Apple")
                            } else {
                                SignInWithAppleButton(.signIn) { request in
                                    request.requestedScopes = [.fullName, .email]
                                } onCompletion: { result in
                                    Task { @MainActor in await viewModel.completeAppleSignIn(result) }
                                }
                                .signInWithAppleButtonStyle(colorScheme == .dark ? .white : .black)
                                .frame(maxWidth: .infinity, minHeight: 50)
                                .clipShape(RoundedRectangle(cornerRadius: 8, style: .continuous))
                                .disabled(!canUseApple)
                                .opacity(canUseApple ? 1 : 0.45)
                                .accessibilityLabel("Sign in with Apple")
                            }

                            if let message = configurationMessage {
                                Label(message, systemImage: "wrench.and.screwdriver")
                                    .font(PlayboardTypography.eyebrow())
                                    .foregroundStyle(palette.textMuted)
                                    .fixedSize(horizontal: false, vertical: true)
                            }

                            if let errorMessage = viewModel.state.errorMessage {
                                VStack(spacing: PlayboardSpacing.small) {
                                    Label(errorMessage, systemImage: "exclamationmark.triangle")
                                        .font(PlayboardTypography.body())
                                        .foregroundStyle(palette.statLoss)
                                        .fixedSize(horizontal: false, vertical: true)
                                        .accessibilityIdentifier("login-error")
                                    Button("Dismiss") { viewModel.dismissError() }
                                        .font(PlayboardTypography.label())
                                        .frame(minHeight: 44)
                                }
                                .accessibilityElement(children: .combine)
                            }
                        }
                    }
                    .padding(.horizontal, PlayboardSpacing.large)
                    Spacer(minLength: 48)
                }
                .frame(maxWidth: 560)
                .frame(maxWidth: .infinity)
            }
            .accessibilityIdentifier("login-screen")
        }
    }

    private var canUseGoogle: Bool {
        !viewModel.state.isLoading && configuration.apiBaseURL != nil && configuration.googleClientID != nil
    }

    private var canUseApple: Bool {
        !viewModel.state.isLoading && configuration.apiBaseURL != nil
    }

    private var configurationMessage: String? {
        if configuration.apiBaseURL == nil {
            return "This build has no Playboard API configured."
        }
        if configuration.googleClientID == nil {
            return "Google sign-in is unavailable in this build. Apple sign-in can still be used."
        }
        return nil
    }

    private func providerProgress(_ message: String) -> some View {
        HStack(spacing: PlayboardSpacing.medium) {
            ProgressView()
            Text(message).font(PlayboardTypography.label())
        }
        .frame(maxWidth: .infinity, minHeight: 50)
        .foregroundStyle(palette.textPrimary)
        .accessibilityElement(children: .combine)
        .accessibilityLabel(message)
    }
}
