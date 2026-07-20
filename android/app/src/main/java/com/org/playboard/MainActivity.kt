package com.org.playboard

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.org.playboard.data.group.GroupRepository
import com.org.playboard.data.settings.ThemeStore
import com.org.playboard.notifications.PlayboardMessagingService
import com.org.playboard.ui.navigation.PlayboardNavHost
import com.org.playboard.ui.theme.PlayboardTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var themeStore: ThemeStore

    @Inject
    lateinit var groupRepository: GroupRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyNotificationDeepLink(intent)
        enableEdgeToEdge()
        setContent {
            val darkTheme by themeStore.isDarkTheme.collectAsState(initial = true)
            val view = LocalView.current
            // Keep the status/navigation bar icons legible against the app content:
            // dark icons on the light theme, light icons on the dark theme.
            LaunchedEffect(darkTheme) {
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !darkTheme
                controller.isAppearanceLightNavigationBars = !darkTheme
            }
            PlayboardTheme(darkTheme = darkTheme) {
                RequestNotificationPermission()
                PlayboardNavHost()
            }
        }
    }

    /** The activity is `singleTop` from the notification's flags, so a tap on a warm app lands here. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        applyNotificationDeepLink(intent)
    }

    /**
     * Switches the active group to the one a tapped notification refers to, so a push
     * about a group you aren't currently viewing doesn't drop you on another group's
     * board. Screens observe [GroupRepository.selectedGroup], so they follow along.
     *
     * Reads the extra for both delivery paths: the foreground notification sets it
     * explicitly, and for a system-rendered background push FCM copies the data payload
     * onto the launch intent under the same key.
     */
    private fun applyNotificationDeepLink(intent: Intent?) {
        val groupId = intent?.getStringExtra(PlayboardMessagingService.EXTRA_GROUP_ID) ?: return
        groupRepository.selectGroup(groupId)
    }

    /**
     * Asks for POST_NOTIFICATIONS once on first composition. Only Android 13+
     * (API 33) gates notifications behind a runtime permission; on older
     * versions posting is allowed by default, so this is a no-op there.
     */
    @androidx.compose.runtime.Composable
    private fun RequestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val launcher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { /* Best-effort: if denied, the user simply won't see pushes. */ }
        LaunchedEffect(Unit) {
            val granted = ContextCompat.checkSelfPermission(
                this@MainActivity,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
