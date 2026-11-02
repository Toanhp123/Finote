package io.github.toanhp123.finote.ui.common.theme

import androidx.compose.ui.graphics.Brush

data class FinoteBrushes(
    val hero: Brush,
)

internal fun finoteBrushes(colors: FinoteColors): FinoteBrushes =
    FinoteBrushes(hero = Brush.linearGradient(listOf(colors.hero.start, colors.hero.end)))
