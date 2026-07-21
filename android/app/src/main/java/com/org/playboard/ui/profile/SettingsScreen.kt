package com.org.playboard.ui.profile

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.res.painterResource
import com.org.playboard.BuildConfig
import com.org.playboard.R
import com.org.playboard.ui.components.PlayboardBackground
import com.org.playboard.ui.theme.PlayboardTheme
import com.org.playboard.ui.update.AppUpdateViewModel

/** Account and application settings opened from the signed-in user's profile. */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onSignOut: (() -> Unit)? = null,
    updateViewModel: AppUpdateViewModel? = null,
    viewModel: ProfileViewModel = hiltViewModel(),
    settingsViewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val isDarkTheme by settingsViewModel.isDarkTheme.collectAsState()

    SettingsScreenContent(
        email = state.email.orEmpty(),
        versionName = BuildConfig.VERSION_NAME,
        isDarkTheme = isDarkTheme,
        onBack = onBack,
        onSignOut = { (onSignOut ?: viewModel::onSignOutClicked)() },
        onCheckForUpdates = { updateViewModel?.checkForUpdate(showResult = true) },
        onDarkThemeChange = settingsViewModel::setDarkTheme,
    )
}

@Composable
private fun SettingsScreenContent(
    email: String,
    versionName: String,
    isDarkTheme: Boolean,
    onBack: () -> Unit,
    onSignOut: () -> Unit,
    onCheckForUpdates: () -> Unit,
    onDarkThemeChange: (Boolean) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 10.dp),
    ) {
        BackRowForSettings(onBack = onBack)
        Text(
            text = "Settings",
            color = PlayboardTheme.colors.textPrimary,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
        )
        Text(
            text = "ACCOUNT",
            color = PlayboardTheme.colors.textMuted,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 24.dp, bottom = 12.dp),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
        ) {

            Spacer(Modifier.size(12.dp))
            Column {
                Text(
                    "Signed in with Google",
                    color = PlayboardTheme.colors.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                )
                Text(email, color = PlayboardTheme.colors.textMuted, fontSize = 12.sp)
            }
        }
        HorizontalDivider(color = PlayboardTheme.colors.textMuted.copy(alpha = 0.25f))
        TextButton(
            onClick = onCheckForUpdates,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Check for updates",
                    color = PlayboardTheme.colors.brand,
                    modifier = Modifier.weight(1f).align(Alignment.CenterVertically),
                )
                Text("›", color = PlayboardTheme.colors.textMuted, fontSize = 28.sp)
            }
        }
        HorizontalDivider(color = PlayboardTheme.colors.textMuted.copy(alpha = 0.25f))
        TextButton(
            onClick = onSignOut,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Sign out",
                    color = PlayboardTheme.colors.textMuted,
                    modifier = Modifier.weight(1f).align(Alignment.CenterVertically),
                )
                Text("›", color = PlayboardTheme.colors.textMuted, fontSize = 28.sp)
            }
        }
        Text(
            text = "APPEARANCE",
            color = PlayboardTheme.colors.textMuted,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 24.dp, bottom = 12.dp),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
        ) {
            Text(
                "Dark theme",
                color = PlayboardTheme.colors.textPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = isDarkTheme,
                onCheckedChange = onDarkThemeChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = PlayboardTheme.colors.onBrand,
                    checkedTrackColor = PlayboardTheme.colors.brand,
                ),
            )
        }
        Spacer(Modifier.weight(1f))
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
        ) {
            Text(
                text = "Playboard v$versionName",
                color = PlayboardTheme.colors.textMuted,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.size(4.dp))
            Text(
                text = "Made by Sriram Elangovan",
                color = PlayboardTheme.colors.textMuted.copy(alpha = 0.7f),
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.size(6.dp))
            Text(
                text = "Avatars: “3D Web3 Avatars” by Koncepted (Figma Community)",
                color = PlayboardTheme.colors.textMuted.copy(alpha = 0.7f),
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun BackRowForSettings(onBack: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
    ) {
        TextButton(onClick = onBack) {
            Icon(
                painter = painterResource(R.drawable.ic_back_arrow),
                contentDescription = null, // "Profile" alongside already labels the button
                tint = PlayboardTheme.colors.brand,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text("Profile", color = PlayboardTheme.colors.brand)
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0A0A0A)
@Composable
private fun SettingsScreenPreview() {
    PlayboardTheme(darkTheme = true) {
        PlayboardBackground {
            SettingsScreenContent(
                email = "raj@gmail.com",
                versionName = "1.8",
                isDarkTheme = true,
                onBack = {},
                onSignOut = {},
                onCheckForUpdates = {},
                onDarkThemeChange = {},
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFAFAFA)
@Composable
private fun SettingsScreenLightPreview() {
    PlayboardTheme(darkTheme = false) {
        PlayboardBackground {
            SettingsScreenContent(
                email = "raj@gmail.com",
                versionName = "1.8",
                isDarkTheme = false,
                onBack = {},
                onSignOut = {},
                onCheckForUpdates = {},
                onDarkThemeChange = {},
            )
        }
    }
}
