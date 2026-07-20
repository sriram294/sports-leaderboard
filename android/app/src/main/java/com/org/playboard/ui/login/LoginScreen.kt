package com.org.playboard.ui.login

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.org.playboard.R
import com.org.playboard.ui.components.AppWordmark
import com.org.playboard.ui.theme.PlayboardTheme

/** Entry point / auth gate — see docs/requirements/01-login.md, docs/prototype/login.pdf. */
@Composable
fun LoginScreen(viewModel: LoginViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    LoginContent(uiState = uiState, onContinueWithGoogleClicked = viewModel::onContinueWithGoogleClicked)
}

@Composable
private fun LoginContent(uiState: LoginUiState, onContinueWithGoogleClicked: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.background),
                )
            )
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 32.dp)) {
            Spacer(modifier = Modifier.weight(1f))

            AppWordmark(modifier = Modifier.fillMaxWidth())

            Spacer(modifier = Modifier.weight(1f))

            if (uiState.error != null) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                ) {
                    Text(
                        text = uiState.error.toMessage(),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                    )
                    // Short, non-sensitive diagnostic code so a user hitting a login
                    // failure in the field can screenshot/copy it into a bug report.
                    val detail = (uiState.error as? LoginError.Generic)?.detail
                    if (detail != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        SelectionContainer {
                            Text(
                                text = "Error code: $detail",
                                color = PlayboardTheme.colors.textMuted,
                                style = MaterialTheme.typography.labelSmall,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }

            Button(
                onClick = onContinueWithGoogleClicked,
                enabled = !uiState.isLoading,
                shape = RoundedCornerShape(percent = 50),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                // Subtle outline so the white button stays visible on the light theme's near-white background.
                border = BorderStroke(1.dp, PlayboardTheme.colors.textMuted.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.Black, strokeWidth = 2.dp)
                } else {
                    Image(
                        painter = painterResource(R.drawable.ic_google_logo),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(text = "Continue with Google", fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "BY CONTINUING YOU AGREE TO THE TERMS",
                style = MaterialTheme.typography.labelSmall,
                color = PlayboardTheme.colors.textMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private fun LoginError.toMessage(): String = when (this) {
    LoginError.NoGoogleAccount -> "No Google account found on this device."
    is LoginError.Generic -> "Something went wrong. Please try again."
}

@Preview(showBackground = true)
@Composable
private fun LoginContentPreview() {
    PlayboardTheme {
        LoginContent(uiState = LoginUiState(), onContinueWithGoogleClicked = {})
    }
}

@Preview(showBackground = true)
@Composable
private fun LoginContentErrorPreview() {
    PlayboardTheme {
        LoginContent(uiState = LoginUiState(error = LoginError.NoGoogleAccount), onContinueWithGoogleClicked = {})
    }
}

@Preview(showBackground = true)
@Composable
private fun LoginContentGenericErrorPreview() {
    PlayboardTheme {
        LoginContent(
            uiState = LoginUiState(error = LoginError.Generic("backend 401")),
            onContinueWithGoogleClicked = {},
        )
    }
}
