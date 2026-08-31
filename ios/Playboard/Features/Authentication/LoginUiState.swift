import Foundation

/// Immutable presentation state for native provider sign-in.
struct LoginUiState: Equatable, Sendable {
    enum ActiveProvider: Equatable, Sendable {
        case google
        case apple
    }

    var activeProvider: ActiveProvider?
    var errorMessage: String?

    var isLoading: Bool { activeProvider != nil }

    static let idle = LoginUiState(activeProvider: nil, errorMessage: nil)
}
