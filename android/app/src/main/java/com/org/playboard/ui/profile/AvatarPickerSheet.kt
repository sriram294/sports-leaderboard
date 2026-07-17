package com.org.playboard.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.org.playboard.ui.components.avatarAssetUrl
import com.org.playboard.ui.theme.PlayboardTheme

/**
 * Lets the signed-in user pick one of the bundled default avatars, or fall back
 * to uploading a photo (docs/requirements/05-profile.md req #3). The 25 avatars
 * scroll in a single horizontal row; picking one replaces any uploaded photo.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AvatarPickerSheet(
    avatarIds: List<String>,
    onPickAvatar: (String) -> Unit,
    onUploadPhoto: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = PlayboardTheme.colors.surface,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 12.dp),
        ) {
            Text(
                text = "Choose your avatar",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = PlayboardTheme.colors.textPrimary,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            Spacer(Modifier.height(16.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(horizontal = 20.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                items(avatarIds, key = { it }) { id ->
                    AsyncImage(
                        model = avatarAssetUrl(id),
                        contentDescription = "Avatar option",
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(PlayboardTheme.colors.background)
                            .clickable { onPickAvatar(id) },
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            TextButton(
                onClick = onUploadPhoto,
                modifier = Modifier.padding(horizontal = 12.dp),
            ) {
                Text(
                    text = "Upload a photo instead",
                    color = PlayboardTheme.colors.brand,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}
