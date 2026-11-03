package io.github.toanhp123.finote.ui.common.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import io.github.toanhp123.finote.ui.common.theme.FinoteTheme

/**
 * A row at least as tall as a touch target. [trailing] keeps its full width next to the title.
 * When that would leave the title narrower than the theme minimum, [trailing] moves to its own line below the title.
 */
@Composable
fun FinoteListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val press = if (onClick == null) Modifier else Modifier.clickable(role = Role.Button, onClick = onClick)
    val metrics =
        ListRowMetrics(
            gap = FinoteTheme.spacing.md,
            stackGap = FinoteTheme.spacing.xs,
            minTitleWidth = FinoteTheme.sizes.titleMinWidth,
            minHeight = FinoteTheme.sizes.touchTarget,
        )
    Layout(
        content = {
            if (leading != null) Box(Modifier.layoutId(ListRowSlot.Leading)) { leading() }
            RowText(title = title, subtitle = subtitle, modifier = Modifier.layoutId(ListRowSlot.Text))
            if (trailing != null) Box(Modifier.layoutId(ListRowSlot.Trailing)) { trailing() }
        },
        modifier = modifier.fillMaxWidth().then(press),
        measurePolicy = { measurables, constraints -> measureListRow(measurables, constraints, metrics) },
    )
}

@Composable
private fun RowText(
    title: String,
    subtitle: String?,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Text(
            text = title,
            color = FinoteTheme.scheme.onSurface,
            style = FinoteTheme.typography.material.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                color = FinoteTheme.scheme.onSurfaceVariant,
                style = FinoteTheme.typography.material.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@FinotePreviews
@Composable
private fun FinoteListRowPreview() {
    FinotePreviewSurface {
        Column {
            FinoteListRow(title = "Title only")
            FinoteListRow(title = "Title and subtitle", subtitle = "Subtitle", onClick = {})
        }
    }
}
