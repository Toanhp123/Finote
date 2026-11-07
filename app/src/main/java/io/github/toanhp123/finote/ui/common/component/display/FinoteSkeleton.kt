package io.github.toanhp123.finote.ui.common.component.display

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Shape
import io.github.toanhp123.finote.ui.common.component.preview.FinotePreviewSurface
import io.github.toanhp123.finote.ui.common.component.preview.FinotePreviews
import io.github.toanhp123.finote.ui.common.theme.FinoteTheme
import io.github.toanhp123.finote.ui.common.theme.shimmerBrush

/** A grey block with a light band sweeping across it, a placeholder while content loads. The caller sets its size. */
@Composable
fun FinoteSkeleton(
    modifier: Modifier = Modifier,
    shape: Shape = FinoteTheme.shapes.sm,
) {
    val progress by rememberInfiniteTransition(label = "skeleton").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = FinoteTheme.motion.shimmerMillis, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
        label = "skeletonShimmer",
    )
    SkeletonSurface(progress = { progress }, modifier = modifier, shape = shape)
}

/** The block of [FinoteSkeleton] at a fixed point of the sweep, so a screenshot can show the band. */
@Composable
internal fun SkeletonSurface(
    progress: () -> Float,
    modifier: Modifier = Modifier,
    shape: Shape = FinoteTheme.shapes.sm,
) {
    val scheme = FinoteTheme.scheme
    val highlight = if (FinoteTheme.isDark) scheme.surfaceContainerHighest else scheme.surfaceContainerLowest
    Box(
        modifier =
            modifier
                .clip(shape)
                .background(scheme.surfaceContainerHigh)
                .drawBehind { drawRect(shimmerBrush(highlight, progress(), size.width)) },
    )
}

@FinotePreviews
@Composable
private fun FinoteSkeletonPreview() {
    FinotePreviewSurface {
        Column(verticalArrangement = Arrangement.spacedBy(FinoteTheme.spacing.sm)) {
            FinoteSkeleton(Modifier.fillMaxWidth().height(FinoteTheme.sizes.touchTarget))
            FinoteSkeleton(Modifier.size(FinoteTheme.sizes.avatar), shape = FinoteTheme.shapes.full)
        }
    }
}
