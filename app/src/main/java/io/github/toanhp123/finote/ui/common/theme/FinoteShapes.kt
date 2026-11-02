package io.github.toanhp123.finote.ui.common.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Shape

data class FinoteShapes(
    val sm: Shape,
    val md: Shape,
    val lg: Shape,
    val xl: Shape,
    val full: Shape,
    val sheet: Shape,
)

internal fun finoteShapes(radii: FinoteRadii): FinoteShapes =
    FinoteShapes(
        sm = RoundedCornerShape(radii.sm),
        md = RoundedCornerShape(radii.md),
        lg = RoundedCornerShape(radii.lg),
        xl = RoundedCornerShape(radii.xl),
        full = RoundedCornerShape(percent = 50),
        sheet = RoundedCornerShape(topStart = radii.xl, topEnd = radii.xl),
    )
