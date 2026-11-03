package io.github.toanhp123.finote.ui.common.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.hazeBlur
import io.github.toanhp123.finote.ui.common.theme.FinoteTheme

/** The state [FinoteScaffold] shares with the glass surfaces drawn over its content. Null outside a scaffold. */
internal val LocalHazeState = staticCompositionLocalOf<HazeState?> { null }

/**
 * A glass surface in [shape]: the content behind is blurred under a translucent fill.
 * Without blur (Android 11 and below, or outside a scaffold) it is a near-opaque fill so text stays readable.
 */
@Composable
internal fun Modifier.finoteGlass(shape: Shape): Modifier {
    val glass = FinoteTheme.colors.glass
    val hazeState = LocalHazeState.current
    val radius = FinoteTheme.sizes.glassBlur
    val style = remember(radius) { HazeBlurStyle { blurRadius(radius) } }
    val surface =
        if (LocalBlurEnabled.current && hazeState != null) {
            clip(shape).hazeBlur(input = HazeInput.Sources(hazeState), style = style).background(glass.fill)
        } else {
            clip(shape).background(glass.solidFill)
        }
    return surface.border(FinoteTheme.sizes.border, glass.stroke, shape)
}
