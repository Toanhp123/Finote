package io.github.toanhp123.finote.ui.common.component.control

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import io.github.toanhp123.finote.ui.common.component.preview.FinotePreviewSurface
import io.github.toanhp123.finote.ui.common.component.preview.FinotePreviews
import io.github.toanhp123.finote.ui.common.theme.FinoteTheme
import kotlin.math.roundToInt

/** Equal-width choices in one frame. The selected one has a filled pill that slides to the new choice. */
@Composable
fun <T> FinoteSegmented(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier,
) {
    val position by animateFloatAsState(
        targetValue = options.indexOf(selected).coerceAtLeast(0).toFloat(),
        animationSpec = FinoteTheme.motion.emphasized,
        label = "segmentedPosition",
    )
    Box(
        modifier =
            modifier
                .clip(FinoteTheme.shapes.md)
                .background(FinoteTheme.colors.card)
                .border(FinoteTheme.sizes.border, FinoteTheme.colors.glass.stroke, FinoteTheme.shapes.md),
    ) {
        SegmentPill(position = { position }, count = options.size)
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min).selectableGroup()) {
            options.forEach { option ->
                SegmentItem(
                    text = label(option),
                    isSelected = option == selected,
                    onClick = { onSelect(option) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** The filled pill behind the selected item. [position] is the item index, fractional while it slides. */
@Composable
private fun BoxScope.SegmentPill(
    position: () -> Float,
    count: Int,
) {
    Box(
        modifier =
            Modifier
                .matchParentSize()
                .layout { measurable, constraints ->
                    val itemWidth = constraints.maxWidth / count.coerceAtLeast(1)
                    val pill = measurable.measure(Constraints.fixed(itemWidth, constraints.maxHeight))
                    layout(constraints.maxWidth, constraints.maxHeight) {
                        pill.placeRelative((position() * itemWidth).roundToInt(), 0)
                    }
                }.padding(FinoteTheme.spacing.xs)
                .clip(FinoteTheme.shapes.sm)
                .background(FinoteTheme.scheme.primary),
    )
}

@Composable
private fun SegmentItem(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .fillMaxHeight()
                .heightIn(min = FinoteTheme.sizes.touchTarget)
                .selectable(selected = isSelected, role = Role.Tab, onClick = onClick)
                .padding(horizontal = FinoteTheme.spacing.sm, vertical = FinoteTheme.spacing.xs),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (isSelected) FinoteTheme.scheme.onPrimary else FinoteTheme.scheme.onSurface,
            style = FinoteTheme.typography.material.labelLarge,
            textAlign = TextAlign.Center,
        )
    }
}

@FinotePreviews
@Composable
private fun FinoteSegmentedPreview() {
    FinotePreviewSurface {
        FinoteSegmented(
            options = listOf("Expense", "Income", "Transfer"),
            selected = "Income",
            onSelect = {},
            label = { it },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
