import Foundation

/// Immutable presentation state for native provider sign-in.
struct LoginUiState: Equatable, Sendable {
    enum ActiveProvider: Equatable, Sendable {
        case google
        case apple
        case reviewer
    }

    var activeProvider: ActiveProvider?
    var errorMessage: String?
    /// Hidden store-review username/password form, revealed by long-pressing the wordmark.
    var isReviewerFormVisible = false
    var reviewerUsername = ""
    var reviewerPassword = ""

    var isLoading: Bool { activeProvider != nil }
    var canSubmitReviewer: Bool {
        !isLoading && !reviewerUsername.trimmingCharacters(in: .whitespaces).isEmpty && !reviewerPassword.isEmpty
    }

    static let idle = LoginUiState(activeProvider: nil, errorMessage: nil)
}
