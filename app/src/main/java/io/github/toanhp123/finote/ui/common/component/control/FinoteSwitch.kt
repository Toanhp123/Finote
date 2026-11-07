package io.github.toanhp123.finote.ui.common.component.control

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.lerp
import com.composeunstyled.Thumb
import com.composeunstyled.Track
import com.composeunstyled.UnstyledSwitch
import io.github.toanhp123.finote.ui.common.component.preview.FinotePreviewSurface
import io.github.toanhp123.finote.ui.common.component.preview.FinotePreviews
import io.github.toanhp123.finote.ui.common.theme.FinoteTheme

/** An on/off switch with a touch target of the theme size. Name it with text next to it; the switch has no label. */
@Composable
fun FinoteSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val scheme = FinoteTheme.scheme
    val progress by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = FinoteTheme.motion.standard,
        label = "switchProgress",
    )
    val sizes = FinoteTheme.sizes
    UnstyledSwitch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier =
            modifier
                .sizeIn(minWidth = sizes.touchTarget, minHeight = sizes.touchTarget)
                .alpha(if (enabled) 1f else FinoteTheme.colors.disabledAlpha),
        enabled = enabled,
    ) {
        Track(
            modifier =
                Modifier
                    .padding(vertical = (sizes.touchTarget - sizes.switchTrackHeight) / 2)
                    .size(sizes.switchTrackWidth, sizes.switchTrackHeight)
                    .clip(FinoteTheme.shapes.full)
                    .background(lerp(scheme.surfaceContainerHighest, scheme.primary, progress))
                    .border(sizes.border, lerp(scheme.outline, scheme.primary, progress), FinoteTheme.shapes.full)
                    .padding(FinoteTheme.spacing.xs),
        ) {
            Thumb(
                modifier =
                    Modifier
                        .size(sizes.switchThumb)
                        .clip(FinoteTheme.shapes.full)
                        .background(lerp(scheme.outline, scheme.onPrimary, progress)),
            ) {}
        }
    }
}

@FinotePreviews
@Composable
private fun FinoteSwitchPreview() {
    FinotePreviewSurface {
        Row(horizontalArrangement = Arrangement.spacedBy(FinoteTheme.spacing.sm)) {
            FinoteSwitch(checked = false, onCheckedChange = {})
            FinoteSwitch(checked = true, onCheckedChange = {})
            FinoteSwitch(checked = false, onCheckedChange = {}, enabled = false)
            FinoteSwitch(checked = true, onCheckedChange = {}, enabled = false)
        }
    }
}
