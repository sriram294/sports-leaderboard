package com.org.playboard.ui.update

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.org.playboard.data.update.AppUpdate
import com.org.playboard.data.update.AppUpdateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface AppUpdateState {
    data object Idle : AppUpdateState
    data class Checking(val showProgress: Boolean) : AppUpdateState
    data class UpToDate(val showConfirmation: Boolean) : AppUpdateState
    data class Available(val update: AppUpdate) : AppUpdateState
    data class Downloading(val update: AppUpdate, val progress: Int) : AppUpdateState
    data class ReadyToInstall(val file: File) : AppUpdateState
    data object HandedOff : AppUpdateState
    data class Error(val message: String) : AppUpdateState
}

@HiltViewModel
class AppUpdateViewModel @Inject constructor(
    private val repository: AppUpdateRepository,
) : ViewModel() {
    private val _state = MutableStateFlow<AppUpdateState>(AppUpdateState.Idle)
    val state: StateFlow<AppUpdateState> = _state.asStateFlow()

    fun checkForUpdate(showResult: Boolean = false) {
        if (_state.value is AppUpdateState.Checking || _state.value is AppUpdateState.Downloading) return
        viewModelScope.launch {
            _state.value = AppUpdateState.Checking(showResult)
            runCatching { repository.findUpdate() }
                .onSuccess { _state.value = it?.let(AppUpdateState::Available) ?: AppUpdateState.UpToDate(showResult) }
                .onFailure { _state.value = AppUpdateState.Error("Couldn't check for updates.") }
        }
    }

    fun downloadUpdate(update: AppUpdate) {
        if (_state.value is AppUpdateState.Downloading) return
        viewModelScope.launch {
            _state.value = AppUpdateState.Downloading(update, 0)
            runCatching { repository.download(update) { progress -> _state.value = AppUpdateState.Downloading(update, progress) } }
                .onSuccess { _state.value = AppUpdateState.ReadyToInstall(it) }
                .onFailure { _state.value = AppUpdateState.Error("Update download failed. Check your connection and try again.") }
        }
    }

    fun dismiss() { if (_state.value !is AppUpdateState.Downloading) _state.value = AppUpdateState.Idle }
    fun installFailed(file: File, message: String) {
        repository.delete(file)
        _state.value = AppUpdateState.Error(message)
    }
    fun markHandedOff() { _state.value = AppUpdateState.HandedOff }
}
