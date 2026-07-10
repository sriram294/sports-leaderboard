package com.org.playboard.ui.navigation

import androidx.lifecycle.ViewModel
import com.org.playboard.data.auth.AuthRepository
import com.org.playboard.data.model.SessionState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

/** Thin pass-through so [PlayboardNavHost] can observe session state via `hiltViewModel()`. */
@HiltViewModel
class SessionViewModel @Inject constructor(authRepository: AuthRepository) : ViewModel() {
    val sessionState: StateFlow<SessionState> = authRepository.sessionState
}
