package com.org.playboard.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import com.org.playboard.BuildConfig
import com.org.playboard.ui.theme.BrandLime
import com.org.playboard.ui.theme.PlayboardTheme
import com.org.playboard.ui.theme.TextMuted
import com.org.playboard.ui.theme.TextPrimary
import com.org.playboard.ui.update.AppUpdateViewModel

/** Account and application settings opened from the signed-in user's profile. */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onSignOut: (() -> Unit)? = null,
    updateViewModel: AppUpdateViewModel? = null,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    SettingsScreenContent(
        email = state.email.orEmpty(),
        versionName = BuildConfig.VERSION_NAME,
        onBack = onBack,
        onSignOut = { (onSignOut ?: viewModel::onSignOutClicked)() },
        onCheckForUpdates = { updateViewModel?.checkForUpdate(showResult = true) },
    )
}

@Composable
private fun SettingsScreenContent(
    email: String,
    versionName: String,
    onBack: () -> Unit,
    onSignOut: () -> Unit,
    onCheckForUpdates: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 20.dp),
    ) {
        BackRowForSettings(onBack = onBack)
        Text(
            text = "Settings",
            color = TextPrimary,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
        )
        Text(
            text = "ACCOUNT",
            color = TextMuted,
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
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(36.dp).clip(CircleShape).background(TextPrimary),
            ) {
                Text("G", color = MaterialTheme.colorScheme.background, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.size(12.dp))
            Column {
                Text(
                    "Signed in with Google",
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                )
                Text(email, color = TextMuted, fontSize = 12.sp)
            }
        }
        HorizontalDivider(color = TextMuted.copy(alpha = 0.25f))
        TextButton(
            onClick = onCheckForUpdates,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Check for updates",
                    color = BrandLime,
                    modifier = Modifier.weight(1f).align(Alignment.CenterVertically),
                )
                Text("›", color = TextMuted, fontSize = 28.sp)
            }
        }
        HorizontalDivider(color = TextMuted.copy(alpha = 0.25f))
        TextButton(
            onClick = onSignOut,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Sign out",
                    color = TextMuted,
                    modifier = Modifier.weight(1f).align(Alignment.CenterVertically),
                )
                Text("›", color = TextMuted, fontSize = 28.sp)
            }
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
                color = TextMuted,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.size(4.dp))
            Text(
                text = "Made by Sriram Elangovan",
                color = TextMuted.copy(alpha = 0.7f),
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
            Text("←  Profile", color = BrandLime)
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0A0A0A)
@Composable
private fun SettingsScreenPreview() {
    PlayboardTheme {
        SettingsScreenContent(
            email = "raj@gmail.com",
            versionName = "1.8",
            onBack = {},
            onSignOut = {},
            onCheckForUpdates = {},
        )
    }
}
