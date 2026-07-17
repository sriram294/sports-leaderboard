package com.org.playboard.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.org.playboard.data.settings.ThemeStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Backs the app-settings controls on the Settings screen (currently just the
 * theme toggle). Kept separate from [ProfileViewModel] so profile/stats logic
 * stays free of app-wide preference concerns.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val themeStore: ThemeStore,
) : ViewModel() {

    /** `true` when the dark theme is active (the default). */
    val isDarkTheme: StateFlow<Boolean> = themeStore.isDarkTheme.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = true,
    )

    fun setDarkTheme(dark: Boolean) {
        viewModelScope.launch { themeStore.setDarkTheme(dark) }
    }
}
