package com.org.playboard.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.org.playboard.data.settings.ThemeStore
import com.org.playboard.data.user.AccountRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Backs the app-settings controls on the Settings screen (currently just the
 * theme toggle). Kept separate from [ProfileViewModel] so profile/stats logic
 * stays free of app-wide preference concerns.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val themeStore: ThemeStore,
    private val accountRepository: AccountRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            themeStore.isDarkTheme.collect { dark ->
                _uiState.update { it.copy(isDarkTheme = dark) }
            }
        }
    }

    fun setDarkTheme(dark: Boolean) {
        viewModelScope.launch { themeStore.setDarkTheme(dark) }
    }

    fun openDeleteDialog() {
        _uiState.update {
            it.copy(isDeleteDialogOpen = true, deleteConfirmation = "", deleteError = null)
        }
    }

    fun closeDeleteDialog() {
        if (_uiState.value.isDeletingAccount) return
        _uiState.update {
            it.copy(isDeleteDialogOpen = false, deleteConfirmation = "", deleteError = null)
        }
    }

    fun setDeleteConfirmation(value: String) {
        if (_uiState.value.isDeletingAccount) return
        _uiState.update { it.copy(deleteConfirmation = value, deleteError = null) }
    }

    fun deleteAccount() {
        if (!_uiState.value.canDeleteAccount) return
        _uiState.update { it.copy(isDeletingAccount = true, deleteError = null) }
        viewModelScope.launch {
            accountRepository.deleteAccount(SettingsUiState.REQUIRED_CONFIRMATION)
                .onFailure {
                    _uiState.update { state ->
                        state.copy(
                            isDeletingAccount = false,
                            deleteError = "Couldn’t delete your account. Check your connection and try again.",
                        )
                    }
                }
        }
    }
}
