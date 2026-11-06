package io.github.toanhp123.finote.ui.common.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring

data class FinoteMotion(
    val standard: SpringSpec<Float> =
        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
    val emphasized: SpringSpec<Float> =
        spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow),
    // Material 3 emphasized decelerate: fast start, long soft stop. For tweens, such as a page slide.
    val emphasizedDecelerate: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f),
    val pressedScale: Float = 0.96f,
    val shimmerMillis: Int = 1200,
)
