package com.org.playboard.ui.login

/**
 * Login screen state. The reviewer fields stay hidden ([isReviewerLoginVisible] false)
 * until the logo is long-pressed — normal users only ever see Google Sign-In.
 */
data class LoginUiState(
    val isLoading: Boolean = false,
    val error: LoginError? = null,
    val isReviewerLoginVisible: Boolean = false,
    val reviewerUsername: String = "",
    val reviewerPassword: String = "",
) {
    /** Whether the reviewer sign-in button can be pressed. */
    val canSubmitReviewerLogin: Boolean
        get() = !isLoading && reviewerUsername.isNotBlank() && reviewerPassword.isNotEmpty()
}

sealed interface LoginError {
    data object NoGoogleAccount : LoginError

    /** The backend rejected the reviewer username/password (deliberately not saying which). */
    data object InvalidReviewerCredentials : LoginError

    /**
     * A catch-all sign-in failure. [detail] is a short, non-sensitive diagnostic
     * code (e.g. "backend 401", "network unavailable") surfaced under the friendly
     * message so a user in the field can screenshot it — never contains tokens.
     */
    data class Generic(val detail: String? = null) : LoginError
}
