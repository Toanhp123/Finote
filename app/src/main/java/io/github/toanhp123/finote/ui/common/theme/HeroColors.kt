package io.github.toanhp123.finote.ui.common.theme

import androidx.compose.ui.graphics.Color

data class HeroColors(
    val start: Color,
    val end: Color,
    val onHero: Color,
) {
    val chipContainer: Color = onHero.copy(alpha = 0.2f)
}
