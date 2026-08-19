package com.org.playboard.ui.profile

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.org.playboard.data.model.MonthlyFinish
import com.org.playboard.ui.theme.PlayboardTheme

private const val EMPTY_MESSAGE = "Finishing positions are recorded after each month closes."

internal data class FinishPoint(val index: Int, val x: Float, val y: Float, val rank: Int)
internal data class FinishSegment(val from: FinishPoint, val to: FinishPoint)
internal data class FinishGeometry(val lowerBound: Int, val points: List<FinishPoint>, val segments: List<FinishSegment>)

/** Normalized chart geometry: rank 1 maps to y=0 and gaps break adjacent segments. */
internal fun finishGeometry(finishes: List<MonthlyFinish>): FinishGeometry {
    val lowerBound = maxOf(3, finishes.maxOfOrNull { it.qualifiedPlayers } ?: 0)
    val denominator = (finishes.size - 1).coerceAtLeast(1)
    val points = finishes.mapIndexedNotNull { index, finish ->
        finish.rank?.let { rank ->
            FinishPoint(
                index = index,
                x = index.toFloat() / denominator,
                y = (rank.coerceIn(1, lowerBound) - 1).toFloat() / (lowerBound - 1),
                rank = rank,
            )
        }
    }
    val byIndex = points.associateBy(FinishPoint::index)
    val segments = (0 until finishes.lastIndex).mapNotNull { index ->
        val from = byIndex[index]
        val to = byIndex[index + 1]
        if (from != null && to != null) FinishSegment(from, to) else null
    }
    return FinishGeometry(lowerBound, points, segments)
}

internal fun finishesAccessibilitySummary(finishes: List<MonthlyFinish>): String =
    when {
        finishes.isEmpty() -> EMPTY_MESSAGE
        finishes.none { it.rank != null } -> "$EMPTY_MESSAGE ${finishes.joinToString(". ") { it.accessibilityLabel }}"
        else -> finishes.joinToString(". ") { it.accessibilityLabel }
    }

@Composable
internal fun MonthlyFinishesCard(finishes: List<MonthlyFinish>) {
    androidx.compose.foundation.layout.Column {
        Text(
            text = "MONTHLY FINISHES",
            style = MaterialTheme.typography.labelSmall,
            color = PlayboardTheme.colors.textMuted,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = PlayboardTheme.colors.surface,
            modifier = Modifier.fillMaxWidth().clearAndSetSemantics {
                contentDescription = finishesAccessibilitySummary(finishes)
            },
        ) {
            if (finishes.none { it.rank != null }) {
                Box(modifier = Modifier.padding(18.dp), contentAlignment = Alignment.CenterStart) {
                    Text(EMPTY_MESSAGE, color = PlayboardTheme.colors.textMuted, fontSize = 13.sp)
                }
            } else {
                FinishCanvas(finishes)
            }
        }
    }
}

@Composable
private fun FinishCanvas(finishes: List<MonthlyFinish>) {
    val geometry = finishGeometry(finishes)
    val brand = PlayboardTheme.colors.brand
    val muted = PlayboardTheme.colors.textMuted
    val surface = PlayboardTheme.colors.surface
    Canvas(modifier = Modifier.fillMaxWidth().height(220.dp).padding(horizontal = 12.dp, vertical = 12.dp)) {
        val left = 16.dp.toPx()
        val right = size.width - 16.dp.toPx()
        val top = 22.dp.toPx()
        val bottom = size.height - 30.dp.toPx()
        val chartWidth = right - left
        val chartHeight = bottom - top

        listOf(0f, .5f, 1f).forEach { fraction ->
            val y = top + chartHeight * fraction
            drawLine(muted.copy(alpha = .16f), Offset(left, y), Offset(right, y), strokeWidth = 1.dp.toPx())
        }
        fun position(point: FinishPoint) = Offset(left + chartWidth * point.x, top + chartHeight * point.y)
        geometry.segments.forEach { segment ->
            drawLine(brand, position(segment.from), position(segment.to), strokeWidth = 2.dp.toPx())
        }
        geometry.points.forEach { point ->
            drawCircle(surface, 6.dp.toPx(), position(point))
            drawCircle(brand, 4.dp.toPx(), position(point))
        }

        val labelPaint = Paint().apply {
            color = muted.toArgb()
            textSize = 10.sp.toPx()
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        val pointPaint = Paint().apply {
            color = brand.toArgb()
            textSize = 10.sp.toPx()
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
            isAntiAlias = true
        }
        finishes.forEachIndexed { index, finish ->
            val x = left + chartWidth * (index.toFloat() / (finishes.size - 1).coerceAtLeast(1))
            drawContext.canvas.nativeCanvas.drawText(finish.shortMonthLabel, x, size.height - 3.dp.toPx(), labelPaint)
        }
        geometry.points.forEach { point ->
            val p = position(point)
            drawContext.canvas.nativeCanvas.drawText("#${point.rank}", p.x, p.y - 9.dp.toPx(), pointPaint)
        }
    }
}

private fun Color.toArgb(): Int = android.graphics.Color.argb(
    (alpha * 255).toInt(),
    (red * 255).toInt(),
    (green * 255).toInt(),
    (blue * 255).toInt(),
)
