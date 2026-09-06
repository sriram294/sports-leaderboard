package com.org.playboard.ui.profile

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.org.playboard.ui.theme.PlayboardTheme
import java.io.ByteArrayOutputStream

/** Square, center-crop editor shown before a profile photo is uploaded. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AvatarCropSheet(bytes: ByteArray, onCancel: () -> Unit, onConfirm: (ByteArray) -> Unit) {
    val bitmap = remember(bytes) { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }
    var zoom by remember { mutableFloatStateOf(1f) }
    ModalBottomSheet(onDismissRequest = onCancel, containerColor = PlayboardTheme.colors.surface, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(20.dp)) {
            Text("Crop profile photo", color = PlayboardTheme.colors.textPrimary)
            if (bitmap != null) {
                Image(bitmap = bitmap.asImageBitmap(), contentDescription = "Profile photo crop preview", contentScale = ContentScale.Crop, modifier = Modifier.size(260.dp).clip(CircleShape).graphicsLayer { scaleX = zoom; scaleY = zoom })
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Zoom", color = PlayboardTheme.colors.textMuted)
                    Slider(value = zoom, onValueChange = { zoom = it }, valueRange = 1f..3f, modifier = Modifier.weight(1f).padding(start = 12.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TextButton(onClick = onCancel) { Text("Cancel") }
                    TextButton(onClick = { onConfirm(centerCrop(bitmap, zoom)) }) { Text("Use photo") }
                }
            } else {
                Text("That image could not be opened.", color = PlayboardTheme.colors.textMuted)
                TextButton(onClick = onCancel) { Text("Close") }
            }
        }
    }
}

private fun centerCrop(source: Bitmap, zoom: Float): ByteArray {
    val side = (minOf(source.width, source.height) / zoom).toInt().coerceAtLeast(1)
    val cropped = Bitmap.createBitmap(source, (source.width - side) / 2, (source.height - side) / 2, side, side)
    return ByteArrayOutputStream().use { output ->
        cropped.compress(Bitmap.CompressFormat.JPEG, 90, output)
        output.toByteArray()
    }
}
