package com.org.playboard.ui.navigation

sealed class PlayboardDestination(val route: String) {
    /** Shown only while [com.org.playboard.data.auth.TokenStore] is being read from disk. */
    data object Splash : PlayboardDestination("splash")
    data object Login : PlayboardDestination("login")
    data object Home : PlayboardDestination("home")
}
