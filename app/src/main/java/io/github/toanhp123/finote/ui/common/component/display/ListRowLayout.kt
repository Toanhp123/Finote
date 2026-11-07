package io.github.toanhp123.finote.ui.common.component.display

import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.constrainHeight

internal enum class ListRowSlot { Leading, Text, Trailing }

internal data class ListRowMetrics(
    val gap: Dp,
    val stackGap: Dp,
    val minTitleWidth: Dp,
    val minHeight: Dp,
)

/** The three slots of a [FinoteListRow], found by their layout id. Text is always there. */
private class ListRowSlots(
    val leading: Measurable?,
    val text: Measurable,
    val trailing: Measurable?,
)

private fun List<Measurable>.toSlots() =
    ListRowSlots(
        leading = firstOrNull { it.layoutId == ListRowSlot.Leading },
        text = first { it.layoutId == ListRowSlot.Text },
        trailing = firstOrNull { it.layoutId == ListRowSlot.Trailing },
    )

/**
 * One line when the title keeps at least the minimum width next to the full trailing content.
 * Otherwise the trailing content takes its own line under the title, end-aligned.
 * Expects a bounded width. A row inside a horizontal scroll has none.
 */
internal fun MeasureScope.measureListRow(
    measurables: List<Measurable>,
    constraints: Constraints,
    metrics: ListRowMetrics,
): MeasureResult {
    val slots = measurables.toSlots()
    val gap = metrics.gap.roundToPx()
    val loose = constraints.copy(minWidth = 0, minHeight = 0)
    val leading = slots.leading?.measure(loose)
    val textStart = if (leading == null) 0 else leading.width + gap
    val textArea = (constraints.maxWidth - textStart).coerceAtLeast(0)
    val trailingWidth = slots.trailing?.maxIntrinsicWidth(constraints.maxHeight) ?: 0
    val isInline = slots.trailing == null || textArea - gap - trailingWidth >= metrics.minTitleWidth.roundToPx()
    val placement =
        if (isInline) {
            inlinePlacement(slots, loose, textArea, gap, trailingWidth)
        } else {
            stackedPlacement(slots, loose, textArea, metrics.stackGap.roundToPx())
        }
    val height = maxOf(metrics.minHeight.roundToPx(), leading?.height ?: 0, placement.height)
    return layout(constraints.maxWidth, constraints.constrainHeight(height)) {
        leading?.placeRelative(0, (height - leading.height) / 2)
        placement.place(this, textStart, textArea, height)
    }
}

/** Text and trailing content as measured. A stacked row puts the trailing content under the text. */
private class ListRowPlacement(
    val text: Placeable,
    val trailing: Placeable?,
    val stackGap: Int?,
) {
    private val trailingHeight = trailing?.height ?: 0

    /** Height of text and trailing content together. */
    val height = if (stackGap == null) maxOf(text.height, trailingHeight) else text.height + stackGap + trailingHeight

    fun place(
        scope: Placeable.PlacementScope,
        textStart: Int,
        textArea: Int,
        rowHeight: Int,
    ) {
        val top = (rowHeight - height) / 2
        with(scope) {
            if (stackGap == null) {
                text.placeRelative(textStart, (rowHeight - text.height) / 2)
                trailing?.placeRelative(textStart + textArea - trailing.width, (rowHeight - trailing.height) / 2)
            } else {
                text.placeRelative(textStart, top)
                trailing?.placeRelative(textStart + textArea - trailing.width, top + text.height + stackGap)
            }
        }
    }
}

private fun inlinePlacement(
    slots: ListRowSlots,
    loose: Constraints,
    textArea: Int,
    gap: Int,
    trailingWidth: Int,
): ListRowPlacement {
    val trailing = slots.trailing?.measure(loose.copy(maxWidth = trailingWidth.coerceAtMost(textArea)))
    val textWidth = (textArea - (trailing?.width?.plus(gap) ?: 0)).coerceAtLeast(0)
    return ListRowPlacement(slots.text.measure(loose.copy(maxWidth = textWidth)), trailing, stackGap = null)
}

private fun stackedPlacement(
    slots: ListRowSlots,
    loose: Constraints,
    textArea: Int,
    stackGap: Int,
): ListRowPlacement {
    val area = loose.copy(maxWidth = textArea)
    return ListRowPlacement(slots.text.measure(area), slots.trailing?.measure(area), stackGap)
}
