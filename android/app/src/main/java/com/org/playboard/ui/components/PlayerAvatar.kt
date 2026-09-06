package com.org.playboard.ui.components
import com.org.playboard.ui.theme.DarkBrand
import com.org.playboard.ui.theme.DarkOnBrand
import com.org.playboard.ui.theme.PlayboardTheme

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Dialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage

/**
 * Parses a server-assigned `avatarColor` hex string (`#RRGGBB`), falling back
 * to the brand accent if malformed so a bad value can never crash a screen.
 */
fun avatarColor(hex: String): Color =
    runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(DarkBrand)

/**
 * Coil model for a bundled default avatar. The PNGs ship under
 * `assets/avatars/<id>.png`; the backend only persists the id.
 */
fun avatarAssetUrl(avatarId: String): String = "file:///android_asset/avatars/$avatarId.png"

/**
 * Global avatar rule (docs/requirements/00-overview.md § Player / Avatar):
 * the uploaded photo if set, else a bundled default avatar ([avatarId]), else a
 * colored-initial circle. The ring always uses the player's assigned color so a
 * player is recognizable everywhere.
 *
 * The colored initial is always drawn as the base layer, so it also serves as
 * the fallback while the image loads or if it fails to load (e.g. the file is
 * gone) — the [AsyncImage] simply paints over it once it loads successfully,
 * and draws nothing on error, leaving the initial visible instead of a blank.
 * `photoUrl` and `avatarId` are mutually exclusive server-side, but photo wins
 * defensively if both are ever present.
 *
 * @param preloadedImage an already-decoded bitmap to draw directly instead of resolving
 *   [photoUrl]/[avatarId] through [AsyncImage]. Used by the leaderboard share card, whose
 *   offscreen capture can't wait on Coil's async load or draw the hardware bitmaps it decodes
 *   by default — see [com.org.playboard.ui.share.renderAndShareLeaderboard].
 */
@Composable
fun PlayerAvatar(
    displayName: String,
    photoUrl: String?,
    avatarColorHex: String,
    modifier: Modifier = Modifier,
    avatarId: String? = null,
    size: Dp = 40.dp,
    preloadedImage: ImageBitmap? = null,
    enablePreview: Boolean = true,
) {
    val color = avatarColor(avatarColorHex)
    val imageModel = photoUrl ?: avatarId?.let(::avatarAssetUrl)
    var showPreview by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(PlayboardTheme.colors.surface)
            .border(width = size / 18, color = color, shape = CircleShape)
            .then(if (enablePreview) Modifier.clickable { showPreview = true } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = displayName.take(1).uppercase(),
            color = color,
            // Built from the theme style rather than a bare TextStyle so the initial keeps
            // the app's UI face — a fresh TextStyle would drop back to the system default.
            style = LocalTextStyle.current.copy(
                fontWeight = FontWeight.Bold,
                fontSize = (size.value * 0.4).sp,
            ),
        )
        if (preloadedImage != null) {
            Image(
                bitmap = preloadedImage,
                contentDescription = displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size).clip(CircleShape),
            )
        } else if (imageModel != null) {
            AsyncImage(
                model = imageModel,
                contentDescription = displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size).clip(CircleShape),
            )
        }
    }
    if (showPreview) {
        Dialog(onDismissRequest = { showPreview = false }) {
            androidx.compose.material3.Surface(shape = RoundedCornerShape(24.dp), color = PlayboardTheme.colors.surface) {
                androidx.compose.foundation.layout.Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(20.dp)) {
                    PlayerAvatar(displayName, photoUrl, avatarColorHex, avatarId = avatarId, size = 260.dp, preloadedImage = preloadedImage, enablePreview = false)
                    Text(displayName, color = PlayboardTheme.colors.textPrimary, modifier = Modifier.padding(top = 12.dp))
                    TextButton(onClick = { showPreview = false }) { androidx.compose.material3.Text("Close") }
                }
            }
        }
    }
}

/** Group avatar — a filled rounded square with the group's initial, per leaderboard.pdf. */
@Composable
fun GroupAvatar(
    name: String,
    avatarColorHex: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size / 4))
            .background(avatarColor(avatarColorHex)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = name.take(1).uppercase(),
            color = DarkOnBrand,
            style = LocalTextStyle.current.copy(
                fontWeight = FontWeight.Bold,
                fontSize = (size.value * 0.45).sp,
            ),
        )
    }
}
