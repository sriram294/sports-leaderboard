package com.org.playboard.ui.login

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.org.playboard.data.auth.AuthRepository
import com.org.playboard.data.auth.GoogleAuthClient
import com.org.playboard.data.auth.GoogleAuthResult
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Drives the login screen: Google Sign-In, plus the hidden store-review username/password login. */
@HiltViewModel
class LoginViewModel @Inject constructor(
    private val googleAuthClient: GoogleAuthClient,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun onContinueWithGoogleClicked() {
        if (_uiState.value.isLoading) return
        _uiState.update { it.copy(isLoading = true, error = null) }

        viewModelScope.launch {
            // Successful sign-in flips AuthRepository.sessionState to SignedIn,
            // which PlayboardNavHost observes to navigate away — no explicit
            // navigation call needed from here.
            when (val result = googleAuthClient.signIn()) {
                is GoogleAuthResult.Success -> {
                    authRepository.signInWithGoogle(result.idToken)
                        .onSuccess { _uiState.update { it.copy(isLoading = false) } }
                        .onFailure {
                            // AuthRepository already logs the detailed cause; this
                            // marks that the failure reached the UI as a generic error
                            // and carries a short code onto the screen for field reports.
                            Log.e(TAG, "Sign-in failed at backend exchange, showing generic error", it)
                            val detail = authRepository.describeSignInFailure(it)
                            _uiState.update { it.copy(isLoading = false, error = LoginError.Generic(detail)) }
                        }
                }
                GoogleAuthResult.Cancelled -> _uiState.update { it.copy(isLoading = false) }
                GoogleAuthResult.NoCredentialAvailable -> {
                    Log.w(TAG, "No Google account available, showing NoGoogleAccount error")
                    _uiState.update { it.copy(isLoading = false, error = LoginError.NoGoogleAccount) }
                }
                is GoogleAuthResult.Failed -> {
                    Log.e(TAG, "Google credential step failed: ${result.message}")
                    _uiState.update { it.copy(isLoading = false, error = LoginError.Generic(result.detail)) }
                }
            }
        }
    }

    /** Long-press on the logo: reveals the hidden reviewer username/password form. */
    fun onLogoLongPressed() {
        _uiState.update { it.copy(isReviewerLoginVisible = true) }
    }

    fun onReviewerUsernameChanged(value: String) {
        _uiState.update { it.copy(reviewerUsername = value) }
    }

    fun onReviewerPasswordChanged(value: String) {
        _uiState.update { it.copy(reviewerPassword = value) }
    }

    /** Submits the reviewer credentials; success navigates away via the session state, like Google sign-in. */
    fun onReviewerSignInClicked() {
        val state = _uiState.value
        if (!state.canSubmitReviewerLogin) return
        _uiState.update { it.copy(isLoading = true, error = null) }

        viewModelScope.launch {
            authRepository.signInAsReviewer(state.reviewerUsername, state.reviewerPassword)
                .onSuccess { _uiState.update { it.copy(isLoading = false, reviewerPassword = "") } }
                .onFailure { cause ->
                    val error = if (authRepository.isInvalidCredentials(cause)) {
                        LoginError.InvalidReviewerCredentials
                    } else {
                        LoginError.Generic(authRepository.describeSignInFailure(cause))
                    }
                    _uiState.update { it.copy(isLoading = false, error = error, reviewerPassword = "") }
                }
        }
    }

    private companion object {
        const val TAG = "PlayboardAuth"
    }
}
