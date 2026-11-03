package io.github.toanhp123.finote.ui.common.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import io.github.toanhp123.finote.ui.common.theme.CategoryPalette
import io.github.toanhp123.finote.ui.common.theme.FinoteTheme

private const val GREEN_INDEX = 9

/** Category colors for previews: the first and the green entry of the palette. */
internal val PreviewRedArgb: Int = CategoryPalette.first().toArgb()
internal val PreviewGreenArgb: Int = CategoryPalette[GREEN_INDEX].toArgb()

/** Brand theme over the page background, for previews and screenshot tests. Dark mode follows the preview. */
@Composable
internal fun FinotePreviewSurface(content: @Composable () -> Unit) {
    FinoteTheme(dynamicColor = false) {
        Box(Modifier.background(FinoteTheme.colors.background).padding(FinoteTheme.spacing.lg)) {
            content()
        }
    }
}
