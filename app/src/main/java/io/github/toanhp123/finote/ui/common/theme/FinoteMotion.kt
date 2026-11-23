package io.github.toanhp123.finote.ui.common.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

data class FinoteMotion(
    val standard: SpringSpec<Float> =
        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
    val emphasized: SpringSpec<Float> =
        spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow),
    // Material 3 emphasized decelerate: fast start, long soft stop. For tweens, such as a page slide.
    val emphasizedDecelerate: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f),
    val pressedScale: Float = 0.96f,
    val shimmerMillis: Int = 1200,
    /** Duration of the page slide in a shared-axis transition. */
    val pageSlideMillis: Int = 300,
    /** Duration of the outgoing page's fade; the incoming fade starts after it. */
    val pageFadeOutMillis: Int = 90,
    /** Duration of the incoming page's fade. */
    val pageFadeInMillis: Int = 210,
    /** Horizontal distance a page slides during a shared-axis transition. */
    val pageSlideOffset: Dp = 30.dp,
)
