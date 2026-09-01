package com.org.playboard.ui.profile

/** Immutable settings state, including the destructive account-deletion flow. */
data class SettingsUiState(
    val isDarkTheme: Boolean = true,
    val isDeleteDialogOpen: Boolean = false,
    val deleteConfirmation: String = "",
    val isDeletingAccount: Boolean = false,
    val deleteError: String? = null,
) {
    val canDeleteAccount: Boolean
        get() = deleteConfirmation == REQUIRED_CONFIRMATION && !isDeletingAccount

    companion object {
        const val REQUIRED_CONFIRMATION = "DELETE"
    }
}
