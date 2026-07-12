package com.org.playboard.ui.profile

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
import com.org.playboard.ui.theme.BackgroundDark
import com.org.playboard.ui.theme.BrandLime
import com.org.playboard.ui.theme.OnBrandLime
import com.org.playboard.ui.theme.PlayboardTheme
import com.org.playboard.ui.theme.StatLossRed
import com.org.playboard.ui.theme.SurfaceDark
import com.org.playboard.ui.theme.TextMuted
import com.org.playboard.ui.theme.TextPrimary

/**
 * Bottom sheet for renaming the signed-in user, reached via the edit affordance
 * on their own identity card (docs/requirements/05-profile.md req #3). Mirrors
 * [com.org.playboard.ui.switcher.RenameGroupSheet] for a consistent edit UX.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditNameSheet(
    state: EditNameSheetState,
    onInputChanged: (String) -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SurfaceDark,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        EditNameSheetContent(state = state, onInputChanged = onInputChanged, onSubmit = onSubmit)
    }
}

/** The sheet's body, split out from [ModalBottomSheet] so it's previewable. */
@Composable
private fun EditNameSheetContent(
    state: EditNameSheetState,
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
            text = "Edit name",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary,
        )
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = state.input,
            onValueChange = onInputChanged,
            singleLine = true,
            isError = state.hasFailed,
            label = { Text("Display name") },
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
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

        if (state.hasFailed) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Couldn't update your name. Please try again.",
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
                Text(text = "Save", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF141414)
@Composable
private fun EditNameSheetPreview() {
    PlayboardTheme {
        EditNameSheetContent(
            state = EditNameSheetState(input = "Raj"),
            onInputChanged = {},
            onSubmit = {},
        )
    }
}
