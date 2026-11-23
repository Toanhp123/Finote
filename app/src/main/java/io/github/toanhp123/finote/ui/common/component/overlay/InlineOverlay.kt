package io.github.toanhp123.finote.ui.common.component.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import io.github.toanhp123.finote.ui.common.theme.FinoteTheme

private const val INLINE_SCRIM_ALPHA = 0.6f

/**
 * What an overlay draws in `LocalInspectionMode`, where layoutlib has no second window: the scrim over the
 * whole parent, with [content] aligned. It only covers what is drawn before it in the same parent.
 */
@Composable
internal fun InlineOverlay(
    alignment: Alignment,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize().background(FinoteTheme.scheme.scrim.copy(alpha = INLINE_SCRIM_ALPHA)),
        contentAlignment = alignment,
    ) { content() }
}
