package io.github.toanhp123.finote.ui.common.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

data class CategoryIconColors(
    val container: Color,
    val icon: Color,
)

fun Int.toComposeColor(): Color = Color(this)

fun categoryIconColors(
    colorArgb: Int,
    isDark: Boolean,
): CategoryIconColors {
    val color = colorArgb.toComposeColor()
    return if (isDark) {
        CategoryIconColors(color.copy(alpha = 0.24f), lerp(color, Color.White, 0.4f))
    } else {
        CategoryIconColors(color.copy(alpha = 0.16f), color)
    }
}
