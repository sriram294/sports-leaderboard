package com.org.playboard.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.org.playboard.data.auth.AuthRepository
import com.org.playboard.data.model.SessionState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Stub backing the Profile tab placeholder — replaced by the real Profile slice. */
@HiltViewModel
class ProfilePlaceholderViewModel @Inject constructor(private val authRepository: AuthRepository) : ViewModel() {

    val displayName: StateFlow<String?> = authRepository.sessionState
        .map { (it as? SessionState.SignedIn)?.user?.displayName }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun onSignOutClicked() {
        viewModelScope.launch { authRepository.signOut() }
    }
}
