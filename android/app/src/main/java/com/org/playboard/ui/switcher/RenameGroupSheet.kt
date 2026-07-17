package com.org.playboard.ui.switcher

import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.org.playboard.ui.theme.PlayboardTheme

/**
 * Bottom sheet for renaming the active group, reached via the edit icon next to
 * the group name in the switcher. Owner/admin only (the entry point is gated on
 * [com.org.playboard.data.model.Group.canManage]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RenameGroupSheet(
    state: RenameSheetState,
    onInputChanged: (String) -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = PlayboardTheme.colors.surface,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        RenameGroupSheetContent(state = state, onInputChanged = onInputChanged, onSubmit = onSubmit)
    }
}

/** The sheet's body, split out from [ModalBottomSheet] so it's previewable. */
@Composable
private fun RenameGroupSheetContent(
    state: RenameSheetState,
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
        Text(
            text = "Rename group",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = PlayboardTheme.colors.textPrimary,
        )
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = state.input,
            onValueChange = onInputChanged,
            singleLine = true,
            isError = state.hasFailed,
            label = { Text("Group name") },
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { if (state.canSubmit) onSubmit() }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = PlayboardTheme.colors.brand,
                unfocusedBorderColor = PlayboardTheme.colors.textMuted.copy(alpha = 0.4f),
                errorBorderColor = PlayboardTheme.colors.statLoss,
                focusedLabelColor = PlayboardTheme.colors.brand,
                unfocusedLabelColor = PlayboardTheme.colors.textMuted,
                errorLabelColor = PlayboardTheme.colors.statLoss,
                cursorColor = PlayboardTheme.colors.brand,
                focusedTextColor = PlayboardTheme.colors.textPrimary,
                unfocusedTextColor = PlayboardTheme.colors.textPrimary,
                focusedContainerColor = PlayboardTheme.colors.background,
                unfocusedContainerColor = PlayboardTheme.colors.background,
                errorContainerColor = PlayboardTheme.colors.background,
            ),
            modifier = Modifier.fillMaxWidth(),
        )

        if (state.hasFailed) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Couldn't rename the group. Please try again.",
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 13.sp),
                color = PlayboardTheme.colors.statLoss,
            )
        }

        Spacer(modifier = Modifier.height(20.dp))
        Button(
            onClick = onSubmit,
            enabled = state.canSubmit,
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = PlayboardTheme.colors.brand,
                contentColor = PlayboardTheme.colors.onBrand,
                disabledContainerColor = PlayboardTheme.colors.brand.copy(alpha = 0.35f),
                disabledContentColor = PlayboardTheme.colors.onBrand.copy(alpha = 0.6f),
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
        ) {
            if (state.isSubmitting) {
                CircularProgressIndicator(color = PlayboardTheme.colors.onBrand, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
            } else {
                Text(text = "Save", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF141414)
@Composable
private fun RenameGroupSheetPreview() {
    PlayboardTheme {
        RenameGroupSheetContent(
            state = RenameSheetState(groupId = "g1", input = "Saturday Smashers"),
            onInputChanged = {},
            onSubmit = {},
        )
    }
}
