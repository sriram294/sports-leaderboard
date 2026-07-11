package com.org.playboard.ui.switcher

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.org.playboard.ui.theme.BackgroundDark
import com.org.playboard.ui.theme.BrandLime
import com.org.playboard.ui.theme.OnBrandLime
import com.org.playboard.ui.theme.PlayboardTheme
import com.org.playboard.ui.theme.SurfaceDark
import com.org.playboard.ui.theme.TextMuted
import com.org.playboard.ui.theme.TextPrimary

/**
 * Bottom sheet for the "Invite players" action. Generates a shareable invite
 * code for the active group, then offers to copy it or hand it off to the
 * system share sheet. Others redeem it via the Join tab of [GroupActionSheet].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InviteSheet(
    state: InviteSheetState,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SurfaceDark,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        InviteSheetContent(
            state = state,
            onRetry = onRetry,
            onCopy = { code ->
                clipboard.setText(AnnotatedString(code))
                Toast.makeText(context, "Invite code copied", Toast.LENGTH_SHORT).show()
            },
            onShare = { code ->
                val message = "Join \"${state.groupName}\" on Playboard. Use invite code $code " +
                    "in the app: Board → Create or join a group → Join."
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, message)
                }
                context.startActivity(Intent.createChooser(send, null))
            },
        )
    }
}

/** The sheet's body, split out from [ModalBottomSheet] so it's previewable. */
@Composable
private fun InviteSheetContent(
    state: InviteSheetState,
    onRetry: () -> Unit,
    onCopy: (String) -> Unit,
    onShare: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp)
            .padding(bottom = 20.dp),
    ) {
        Text(
            text = "Invite players",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary,
        )
        Text(
            text = "to ${state.groupName}",
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 13.sp),
            color = TextMuted,
        )
        Spacer(modifier = Modifier.height(20.dp))

        when {
            state.code != null -> InviteReady(code = state.code, onCopy = onCopy, onShare = onShare)
            state.hasFailed -> InviteFailed(onRetry = onRetry)
            else -> InviteLoading()
        }
    }
}

@Composable
private fun InviteLoading() {
    Box(modifier = Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = BrandLime, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Generating an invite code…",
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 13.sp),
                color = TextMuted,
            )
        }
    }
}

@Composable
private fun InviteFailed(onRetry: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Couldn't create an invite. Please try again.",
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 14.sp),
            color = TextMuted,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = onRetry,
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = BrandLime, contentColor = OnBrandLime),
        ) {
            Text("Retry", fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun InviteReady(code: String, onCopy: (String) -> Unit, onShare: (String) -> Unit) {
    Column {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(BackgroundDark)
                .border(1.dp, BrandLime.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                .padding(vertical = 20.dp),
        ) {
            Text(
                text = code,
                style = MaterialTheme.typography.displayLarge.copy(fontSize = 32.sp, letterSpacing = 4.sp),
                color = TextPrimary,
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Anyone with this code can join the group from the Join tab.",
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 13.sp),
            color = TextMuted,
        )
        Spacer(modifier = Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { onCopy(code) },
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, TextMuted.copy(alpha = 0.5f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                modifier = Modifier.weight(1f).height(52.dp),
            ) {
                Text("Copy", fontWeight = FontWeight.SemiBold)
            }
            Button(
                onClick = { onShare(code) },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BrandLime, contentColor = OnBrandLime),
                modifier = Modifier.weight(1f).height(52.dp),
            ) {
                Text("Share", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF141414)
@Composable
private fun InviteSheetReadyPreview() {
    PlayboardTheme {
        InviteSheetContent(
            state = InviteSheetState(groupName = "Saturday Smashers", isLoading = false, code = "SMASH42"),
            onRetry = {},
            onCopy = {},
            onShare = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF141414)
@Composable
private fun InviteSheetLoadingPreview() {
    PlayboardTheme {
        InviteSheetContent(
            state = InviteSheetState(groupName = "Saturday Smashers"),
            onRetry = {},
            onCopy = {},
            onShare = {},
        )
    }
}
