package com.org.playboard.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.org.playboard.ui.theme.BrandLime
import com.org.playboard.ui.theme.OnBrandLime
import com.org.playboard.ui.theme.SurfaceDark

/**
 * Parses a server-assigned `avatarColor` hex string (`#RRGGBB`), falling back
 * to the brand accent if malformed so a bad value can never crash a screen.
 */
fun avatarColor(hex: String): Color =
    runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(BrandLime)

/**
 * Global avatar rule (docs/requirements/00-overview.md § Player / Avatar):
 * the uploaded photo if set, else a colored-initial circle. The ring always
 * uses the player's assigned color so a player is recognizable everywhere.
 *
 * The colored initial is always drawn as the base layer, so it also serves as
 * the fallback while the photo loads or if it fails to load (e.g. the file is
 * gone) — the [AsyncImage] simply paints over it once it loads successfully,
 * and draws nothing on error, leaving the initial visible instead of a blank.
 */
@Composable
fun PlayerAvatar(
    displayName: String,
    photoUrl: String?,
    avatarColorHex: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
) {
    val color = avatarColor(avatarColorHex)
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(SurfaceDark)
            .border(width = size / 18, color = color, shape = CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = displayName.take(1).uppercase(),
            color = color,
            style = TextStyle(fontWeight = FontWeight.Bold, fontSize = (size.value * 0.4).sp),
        )
        if (photoUrl != null) {
            AsyncImage(
                model = photoUrl,
                contentDescription = displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size).clip(CircleShape),
            )
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
            color = OnBrandLime,
            style = TextStyle(fontWeight = FontWeight.Bold, fontSize = (size.value * 0.45).sp),
        )
    }
}
