package com.org.playboard.ui.login

data class LoginUiState(
    val isLoading: Boolean = false,
    val error: LoginError? = null,
)

sealed interface LoginError {
    data object NoGoogleAccount : LoginError
    data object Generic : LoginError
}
