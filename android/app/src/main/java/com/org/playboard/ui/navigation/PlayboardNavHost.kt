package com.org.playboard.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.org.playboard.data.model.SessionState
import com.org.playboard.ui.login.LoginScreen
import com.org.playboard.ui.main.MainScreen

/**
 * Start destination is a splash gate, not Login directly — otherwise a
 * returning signed-in user would see a flash of the Login screen before
 * [SessionViewModel.sessionState] resolves from disk. The [LaunchedEffect]
 * reacts to session state continuously (not just at cold start), so a
 * mid-session refresh failure also routes back to Login.
 */
@Composable
fun PlayboardNavHost(
    navController: NavHostController = rememberNavController(),
    sessionViewModel: SessionViewModel = hiltViewModel(),
) {
    val sessionState by sessionViewModel.sessionState.collectAsState()

    LaunchedEffect(sessionState) {
        val target = when (sessionState) {
            is SessionState.SignedIn -> PlayboardDestination.Home.route
            SessionState.SignedOut -> PlayboardDestination.Login.route
            SessionState.Loading -> return@LaunchedEffect
        }
        if (navController.currentDestination?.route != target) {
            navController.navigate(target) {
                popUpTo(navController.graph.id) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    NavHost(navController = navController, startDestination = PlayboardDestination.Splash.route) {
        composable(PlayboardDestination.Splash.route) { SplashScreen() }
        composable(PlayboardDestination.Login.route) { LoginScreen() }
        composable(PlayboardDestination.Home.route) { MainScreen() }
    }
}

@Composable
private fun SplashScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}
