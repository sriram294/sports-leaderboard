package com.org.playboard.data.model

/** Drives navigation: which top-level destination the app should be showing. */
sealed interface SessionState {
    /** Initial state while [com.org.playboard.data.auth.TokenStore] is read from disk. */
    data object Loading : SessionState

    data object SignedOut : SessionState

    data class SignedIn(val user: UserSession) : SessionState
}
