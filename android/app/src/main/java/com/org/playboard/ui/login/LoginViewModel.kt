package com.org.playboard.ui.login

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
import kotlinx.coroutines.launch

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val googleAuthClient: GoogleAuthClient,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun onContinueWithGoogleClicked() {
        if (_uiState.value.isLoading) return
        _uiState.value = LoginUiState(isLoading = true)

        viewModelScope.launch {
            // Successful sign-in flips AuthRepository.sessionState to SignedIn,
            // which PlayboardNavHost observes to navigate away — no explicit
            // navigation call needed from here.
            when (val result = googleAuthClient.signIn()) {
                is GoogleAuthResult.Success -> {
                    authRepository.signInWithGoogle(result.idToken)
                        .onSuccess { _uiState.value = LoginUiState() }
                        .onFailure { _uiState.value = LoginUiState(error = LoginError.Generic) }
                }
                GoogleAuthResult.Cancelled -> _uiState.value = LoginUiState()
                GoogleAuthResult.NoCredentialAvailable ->
                    _uiState.value = LoginUiState(error = LoginError.NoGoogleAccount)
                is GoogleAuthResult.Failed -> _uiState.value = LoginUiState(error = LoginError.Generic)
            }
        }
    }
}
