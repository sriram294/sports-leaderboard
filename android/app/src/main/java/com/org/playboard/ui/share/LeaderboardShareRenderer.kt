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
import androidx.compose.ui.platform.ComposeView
import androidx.core.content.FileProvider
import com.org.playboard.data.auth.ActivityProvider
import com.org.playboard.data.model.Group
import com.org.playboard.data.model.PlayerRanking
import com.org.playboard.ui.theme.PlayboardTheme
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

private const val TAG = "LeaderboardShare"

/**
 * Renders [LeaderboardShareCard] off-screen to a PNG and hands it to the Android
 * share sheet (`ACTION_SEND`, `image/png`).
 *
 * The card is composed in a throwaway [ComposeView] attached to the current
 * Activity's content root (via [ActivityProvider]) — attaching gives Compose the
 * lifecycle/owners it needs. It is then re-measured unbounded so a card taller than
 * the screen is captured whole rather than clipped to the content root. The view is
 * drawn at alpha 0 so it never flashes, then removed. Avatars are initials-only, so
 * there is no async image load to wait on before capturing.
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
        val bitmap = withContext(Dispatchers.Main) { captureCard(activity, group, rankings, minGamesToRank, darkTheme) }
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

/** Composes the card in an attached [ComposeView], waits for its first draw, and snapshots it. */
private suspend fun captureCard(
    activity: Activity,
    group: Group,
    rankings: List<PlayerRanking>,
    minGamesToRank: Int,
    darkTheme: Boolean,
): Bitmap {
    val root = activity.findViewById<ViewGroup>(android.R.id.content)
    val widthPx = (CARD_WIDTH_DP * activity.resources.displayMetrics.density).toInt()
    val composeView = ComposeView(activity).apply {
        alpha = 0f // laid out and drawn, but never visible on screen
        layoutParams = FrameLayout.LayoutParams(widthPx, ViewGroup.LayoutParams.WRAP_CONTENT)
        setContent {
            PlayboardTheme(darkTheme = darkTheme) {
                LeaderboardShareCard(group = group, rankings = rankings, minGamesToRank = minGamesToRank)
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
