package com.org.playboard.ui.board

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.org.playboard.ui.theme.BackgroundDark
import com.org.playboard.ui.theme.BrandLime
import com.org.playboard.ui.theme.OnBrandLime
import com.org.playboard.ui.theme.StatLossRed
import com.org.playboard.ui.theme.PlayboardTheme
import com.org.playboard.ui.theme.SurfaceDark
import com.org.playboard.ui.theme.TextMuted
import com.org.playboard.ui.theme.TextPrimary

/**
 * Bottom sheet for the group switcher's "+ Create or join a group" action.
 * A single surface with a Create/Join toggle over one text field — creating a
 * group only needs a name (the sole sport is fixed server-side), joining needs
 * an invite code.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupActionSheet(
    state: GroupActionSheetState,
    onModeChanged: (GroupActionMode) -> Unit,
    onInputChanged: (String) -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SurfaceDark,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        GroupActionSheetContent(
            state = state,
            onModeChanged = onModeChanged,
            onInputChanged = onInputChanged,
            onSubmit = onSubmit,
        )
    }
}

/** The sheet's body, split out from [ModalBottomSheet] so it's previewable. */
@Composable
private fun GroupActionSheetContent(
    state: GroupActionSheetState,
    onModeChanged: (GroupActionMode) -> Unit,
    onInputChanged: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 20.dp)
            .padding(bottom = 20.dp),
    ) {
        ModeToggle(mode = state.mode, onModeChanged = onModeChanged)
        Spacer(modifier = Modifier.height(20.dp))

        val isJoin = state.mode == GroupActionMode.JOIN
        OutlinedTextField(
            value = state.input,
            onValueChange = onInputChanged,
            singleLine = true,
            isError = state.error != null,
            label = { Text(if (isJoin) "Invite code" else "Group name") },
            placeholder = { Text(if (isJoin) "e.g. SMASH42" else "e.g. Saturday Smashers") },
            keyboardOptions = KeyboardOptions(
                capitalization = if (isJoin) KeyboardCapitalization.Characters else KeyboardCapitalization.Words,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { if (state.canSubmit) onSubmit() }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = BrandLime,
                unfocusedBorderColor = TextMuted.copy(alpha = 0.4f),
                errorBorderColor = StatLossRed,
                focusedLabelColor = BrandLime,
                unfocusedLabelColor = TextMuted,
                errorLabelColor = StatLossRed,
                cursorColor = BrandLime,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                focusedContainerColor = BackgroundDark,
                unfocusedContainerColor = BackgroundDark,
                errorContainerColor = BackgroundDark,
            ),
            modifier = Modifier.fillMaxWidth(),
        )

        if (state.error != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = errorMessage(state.error),
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 13.sp),
                color = StatLossRed,
            )
        }

        Spacer(modifier = Modifier.height(20.dp))
        Button(
            onClick = onSubmit,
            enabled = state.canSubmit,
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = BrandLime,
                contentColor = OnBrandLime,
                disabledContainerColor = BrandLime.copy(alpha = 0.35f),
                disabledContentColor = OnBrandLime.copy(alpha = 0.6f),
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
        ) {
            if (state.isSubmitting) {
                CircularProgressIndicator(color = OnBrandLime, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
            } else {
                Text(
                    text = if (isJoin) "Join group" else "Create group",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun ModeToggle(mode: GroupActionMode, onModeChanged: (GroupActionMode) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(BackgroundDark)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ModeToggleTab("Create", GroupActionMode.CREATE, mode, onModeChanged, Modifier.weight(1f))
        ModeToggleTab("Join", GroupActionMode.JOIN, mode, onModeChanged, Modifier.weight(1f))
    }
}

@Composable
private fun ModeToggleTab(
    label: String,
    tab: GroupActionMode,
    selected: GroupActionMode,
    onModeChanged: (GroupActionMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isSelected = tab == selected
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(RoundedCornerShape(9.dp))
            .background(if (isSelected) BrandLime else androidx.compose.ui.graphics.Color.Transparent)
            .clickable { onModeChanged(tab) }
            .padding(vertical = 10.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = if (isSelected) OnBrandLime else TextMuted,
            textAlign = TextAlign.Center,
        )
    }
}

private fun errorMessage(error: GroupActionError): String = when (error) {
    GroupActionError.INVALID_CODE -> "That invite code didn't work. Check it and try again."
    GroupActionError.NETWORK -> "Something went wrong. Please try again."
}

@Preview(showBackground = true, backgroundColor = 0xFF141414)
@Composable
private fun GroupActionSheetCreatePreview() {
    PlayboardTheme {
        GroupActionSheetContent(
            state = GroupActionSheetState(mode = GroupActionMode.CREATE, input = "Saturday Smashers"),
            onModeChanged = {},
            onInputChanged = {},
            onSubmit = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF141414)
@Composable
private fun GroupActionSheetJoinErrorPreview() {
    PlayboardTheme {
        GroupActionSheetContent(
            state = GroupActionSheetState(
                mode = GroupActionMode.JOIN,
                input = "BADCDE",
                error = GroupActionError.INVALID_CODE,
            ),
            onModeChanged = {},
            onInputChanged = {},
            onSubmit = {},
        )
    }
}
