import AuthenticationServices
import Foundation
import GoogleSignIn
import SwiftUI
import UIKit

/// Injected native Google provider boundary.
@MainActor
protocol GoogleAuthProviding: AnyObject {
    func signIn() async throws -> ProviderCredential
    func signOut()
}

/// Configured Google Sign-In SDK adapter.
@MainActor
final class GoogleAuthProvider: GoogleAuthProviding {
    private let clientID: String?

    init(clientID: String?) {
        self.clientID = clientID
    }

    func signIn() async throws -> ProviderCredential {
        guard let clientID else {
            throw AuthError.notConfigured("Google")
        }
        guard let presentingViewController = UIApplication.shared.playboardTopViewController else {
            throw AuthError.invalidResponse
        }

        GIDSignIn.sharedInstance.configuration = GIDConfiguration(clientID: clientID)
        do {
            let result = try await GIDSignIn.sharedInstance.signIn(withPresenting: presentingViewController)
            guard let identityToken = result.user.idToken?.tokenString else {
                throw AuthError.invalidResponse
            }
            return ProviderCredential(identityToken: identityToken, givenName: nil, familyName: nil)
        } catch let error as NSError
            where error.domain == kGIDSignInErrorDomain
                && error.code == GIDSignInErrorCode.canceled.rawValue {
            throw AuthError.cancelled
        } catch let error as AuthError {
            throw error
        } catch {
            throw AuthError.rejected("Google sign-in could not be completed.")
        }
    }

    func signOut() {
        GIDSignIn.sharedInstance.signOut()
    }
}

/// Injected parser for credentials returned by AuthenticationServices.
@MainActor
protocol AppleCredentialParsing {
    func credential(from authorization: ASAuthorization) throws -> ProviderCredential
}

/// Converts a verified system authorization result into the backend exchange shape.
@MainActor
struct AppleCredentialParser: AppleCredentialParsing {
    func credential(from authorization: ASAuthorization) throws -> ProviderCredential {
        guard let credential = authorization.credential as? ASAuthorizationAppleIDCredential,
              let tokenData = credential.identityToken,
              let identityToken = String(data: tokenData, encoding: .utf8) else {
            throw AuthError.invalidResponse
        }
        return ProviderCredential(
            identityToken: identityToken,
            givenName: credential.fullName?.givenName,
            familyName: credential.fullName?.familyName
        )
    }
}

/// Native Google SDK button bridged into SwiftUI while keeping action ownership in the ViewModel.
struct GoogleSignInButton: UIViewRepresentable {
    let isEnabled: Bool
    let action: () -> Void

    func makeCoordinator() -> Coordinator {
        Coordinator(action: action)
    }

    func makeUIView(context: Context) -> GIDSignInButton {
        let button = GIDSignInButton()
        button.style = .wide
        button.addTarget(context.coordinator, action: #selector(Coordinator.didTap), for: .touchUpInside)
        return button
    }

    func updateUIView(_ button: GIDSignInButton, context: Context) {
        button.isEnabled = isEnabled
        context.coordinator.action = action
    }

    final class Coordinator: NSObject {
        var action: () -> Void

        init(action: @escaping () -> Void) {
            self.action = action
        }

        @objc func didTap() {
            action()
        }
    }
}

@MainActor
private extension UIApplication {
    var playboardTopViewController: UIViewController? {
        let root = connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .flatMap(\.windows)
            .first(where: \.isKeyWindow)?
            .rootViewController
        var current = root
        while let presented = current?.presentedViewController {
            current = presented
        }
        return current
    }
}
