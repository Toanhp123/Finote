package io.github.toanhp123.finote.ui.common.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring

data class FinoteMotion(
    val standard: SpringSpec<Float> =
        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
    val emphasized: SpringSpec<Float> =
        spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow),
    val pressedScale: Float = 0.96f,
    val shimmerMillis: Int = 1200,
)
