package com.org.playboard

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.core.content.ContextCompat
import com.org.playboard.ui.navigation.PlayboardNavHost
import com.org.playboard.ui.theme.PlayboardTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PlayboardTheme {
                RequestNotificationPermission()
                PlayboardNavHost()
            }
        }
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
