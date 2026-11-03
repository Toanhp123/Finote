package io.github.toanhp123.finote.ui.common.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

private const val SHIMMER_ALPHA = 0.5f

/** A soft band of [highlight], one [width] wide. It sweeps left to right across the block as [progress] runs 0 to 1. */
internal fun shimmerBrush(
    highlight: Color,
    progress: Float,
    width: Float,
): Brush {
    val start = (progress * 2f - 1f) * width
    return Brush.horizontalGradient(
        colors = listOf(Color.Transparent, highlight.copy(alpha = SHIMMER_ALPHA), Color.Transparent),
        startX = start,
        endX = start + width,
    )
}
