package com.org.playboard.ui.share

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import android.widget.Toast
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.ComposeView
import androidx.core.content.FileProvider
import coil3.BitmapImage
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import com.org.playboard.data.auth.ActivityProvider
import com.org.playboard.data.model.Group
import com.org.playboard.data.model.PlayerRanking
import com.org.playboard.ui.components.avatarAssetUrl
import com.org.playboard.ui.theme.PlayboardTheme
import java.io.File
import java.io.FileOutputStream
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

private const val TAG = "LeaderboardShare"

/**
 * Renders [LeaderboardShareCard] off-screen to a PNG and hands it to the Android
 * share sheet (`ACTION_SEND`, `image/png`).
 *
 * Player avatars are resolved up front by [preloadAvatars] — before the card is even
 * composed — since the capture snapshots right after the first draw and can't wait on
 * [coil3.compose.AsyncImage]'s own async load or draw the hardware bitmaps it decodes by
 * default. The card is then composed in a throwaway [ComposeView] attached to the current
 * Activity's content root (via [ActivityProvider]) — attaching gives Compose the
 * lifecycle/owners it needs. It is then re-measured unbounded so a card taller than
 * the screen is captured whole rather than clipped to the content root. The view is
 * drawn at alpha 0 so it never flashes, then removed.
 *
 * Kept out of the ViewModel deliberately: it touches Android `View`/`Context`,
 * which the ViewModels must stay free of (see PROJECT_RULES).
 */
suspend fun renderAndShareLeaderboard(
    context: Context,
    group: Group,
    rankings: List<PlayerRanking>,
    minGamesToRank: Int,
    darkTheme: Boolean,
) {
    val activity = ActivityProvider.currentActivity
    if (activity == null) {
        Log.w(TAG, "No foreground Activity; cannot render the leaderboard image.")
        showToast(context, "Couldn't prepare the image")
        return
    }
    try {
        val avatars = preloadAvatars(context, topRankings(rankings))
        val bitmap = withContext(Dispatchers.Main) {
            captureCard(activity, group, rankings, minGamesToRank, darkTheme, avatars)
        }
        val uri = withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "shared").apply { mkdirs() }
            val file = File(dir, shareImageFileName(group.id))
            FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, "${group.name} leaderboard on Playboard")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        activity.startActivity(Intent.createChooser(send, null))
    } catch (e: Exception) {
        Log.e(TAG, "Failed to render/share the leaderboard image", e)
        showToast(context, "Couldn't share the leaderboard")
    }
}

/** Logical width the share card is laid out at, in dp. Fixed (not the device width) so
 *  the rankings table always has room for full player names regardless of screen size. */
private const val CARD_WIDTH_DP = 460f

/**
 * Resolves each of [rankings]'s avatars (uploaded photo, else bundled `avatarId`) to a plain
 * software [ImageBitmap], in parallel. `allowHardware(false)` is the load-bearing bit: Coil
 * otherwise decodes into hardware bitmaps, which [captureCard]'s offscreen software canvas
 * can't draw ("Software rendering doesn't support hardware bitmaps"). A player with neither
 * (or whose load fails) is simply absent from the result — [LeaderboardShareCard] falls back
 * to the colored initial for any userId it can't find here.
 */
private suspend fun preloadAvatars(
    context: Context,
    rankings: List<PlayerRanking>,
): Map<String, ImageBitmap> = coroutineScope {
    val loader = context.imageLoader
    rankings.mapNotNull { entry ->
        val model = entry.photoUrl ?: entry.avatarId?.let(::avatarAssetUrl)
        model?.let { entry.userId to it }
    }.map { (userId, model) ->
        async {
            val request = ImageRequest.Builder(context).data(model).allowHardware(false).build()
            val image = (loader.execute(request) as? SuccessResult)?.image
            userId to (image as? BitmapImage)?.bitmap?.asImageBitmap()
        }
    }.awaitAll().mapNotNull { (userId, bitmap) -> bitmap?.let { userId to it } }.toMap()
}

/** Composes the card in an attached [ComposeView], waits for its first draw, and snapshots it. */
private suspend fun captureCard(
    activity: Activity,
    group: Group,
    rankings: List<PlayerRanking>,
    minGamesToRank: Int,
    darkTheme: Boolean,
    avatars: Map<String, ImageBitmap>,
): Bitmap {
    val root = activity.findViewById<ViewGroup>(android.R.id.content)
    val widthPx = (CARD_WIDTH_DP * activity.resources.displayMetrics.density).toInt()
    val composeView = ComposeView(activity).apply {
        alpha = 0f // laid out and drawn, but never visible on screen
        layoutParams = FrameLayout.LayoutParams(widthPx, ViewGroup.LayoutParams.WRAP_CONTENT)
        setContent {
            PlayboardTheme(darkTheme = darkTheme) {
                LeaderboardShareCard(
                    group = group,
                    rankings = rankings,
                    minGamesToRank = minGamesToRank,
                    avatars = avatars,
                )
            }
        }
    }
    root.addView(composeView)
    try {
        composeView.awaitFirstDraw()
        // Attaching gets Compose its lifecycle owners, but the content root is only as tall
        // as the screen and measures children against that — a card with a full rankings
        // table is taller, so the parent's pass clips its bottom rows. Re-measure with an
        // unbounded height and lay the card out at its true size before snapshotting.
        composeView.measure(
            View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        composeView.layout(0, 0, composeView.measuredWidth, composeView.measuredHeight)
        val bitmap = Bitmap.createBitmap(composeView.measuredWidth, composeView.measuredHeight, Bitmap.Config.ARGB_8888)
        composeView.draw(Canvas(bitmap))
        return bitmap
    } finally {
        root.removeView(composeView)
    }
}

/** Suspends until the view has been composed, laid out with a non-zero size, and is about to draw. */
private suspend fun View.awaitFirstDraw() = suspendCancellableCoroutine { cont ->
    val observer = viewTreeObserver
    val listener = object : ViewTreeObserver.OnDrawListener {
        override fun onDraw() {
            if (width > 0 && height > 0 && cont.isActive) {
                // Can't remove a draw listener from within onDraw(); defer it.
                post { if (viewTreeObserver.isAlive) viewTreeObserver.removeOnDrawListener(this) }
                cont.resume(Unit)
            }
        }
    }
    observer.addOnDrawListener(listener)
    cont.invokeOnCancellation {
        if (observer.isAlive) observer.removeOnDrawListener(listener)
    }
}

private fun showToast(context: Context, message: String) {
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}
