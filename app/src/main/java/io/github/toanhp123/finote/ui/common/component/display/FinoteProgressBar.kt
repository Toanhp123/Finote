package io.github.toanhp123.finote.ui.common.component.display

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import io.github.toanhp123.finote.ui.common.component.preview.FinotePreviewSurface
import io.github.toanhp123.finote.ui.common.component.preview.FinotePreviews
import io.github.toanhp123.finote.ui.common.theme.FinoteTheme

/** Draws no semantics: the row that holds it says the state in words, so color is never the only signal. */
@Composable
fun FinoteProgressBar(
    fraction: Float,
    tone: ProgressTone,
    modifier: Modifier = Modifier,
) {
    val fill: Color =
        when (tone) {
            ProgressTone.Normal -> FinoteTheme.scheme.primary
            ProgressTone.Warning -> FinoteTheme.colors.warning.color
            ProgressTone.Over -> FinoteTheme.scheme.error
        }
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(FinoteTheme.sizes.barHeight)
                .clip(FinoteTheme.shapes.full)
                .background(FinoteTheme.scheme.surfaceVariant),
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .fillMaxHeight()
                .clip(FinoteTheme.shapes.full)
                .background(fill),
        )
    }
}

enum class ProgressTone { Normal, Warning, Over }

@FinotePreviews
@Composable
private fun FinoteProgressBarPreview() {
    FinotePreviewSurface {
        Column(verticalArrangement = Arrangement.spacedBy(FinoteTheme.spacing.lg)) {
            FinoteProgressBar(fraction = 0.4f, tone = ProgressTone.Normal)
            FinoteProgressBar(fraction = 0.85f, tone = ProgressTone.Warning)
            FinoteProgressBar(fraction = 1f, tone = ProgressTone.Over)
            FinoteProgressBar(fraction = 0f, tone = ProgressTone.Normal)
        }
    }
}
