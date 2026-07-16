package com.org.playboard.ui.login

data class LoginUiState(
    val isLoading: Boolean = false,
    val error: LoginError? = null,
)

sealed interface LoginError {
    data object NoGoogleAccount : LoginError

    /**
     * A catch-all sign-in failure. [detail] is a short, non-sensitive diagnostic
     * code (e.g. "backend 401", "network unavailable") surfaced under the friendly
     * message so a user in the field can screenshot it — never contains tokens.
     */
    data class Generic(val detail: String? = null) : LoginError
}
