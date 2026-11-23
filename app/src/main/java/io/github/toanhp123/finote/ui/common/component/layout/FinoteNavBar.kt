package io.github.toanhp123.finote.ui.common.component.layout

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import io.github.toanhp123.finote.R
import io.github.toanhp123.finote.ui.common.component.glass.finoteGlass
import io.github.toanhp123.finote.ui.common.component.preview.FinotePreviewSurface
import io.github.toanhp123.finote.ui.common.component.preview.FinotePreviews
import io.github.toanhp123.finote.ui.common.theme.FinoteTheme

/**
 * A floating glass bar with one tab per item. It keeps a margin from the screen edge.
 * Inside a [FinoteScaffold] the content behind it is blurred.
 */
@Composable
fun FinoteNavBar(
    items: List<FinoteNavItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(FinoteTheme.spacing.md)
                .height(FinoteTheme.sizes.navBarHeight)
                .finoteGlass(FinoteTheme.shapes.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEachIndexed { index, item ->
            NavTab(
                item = item,
                selected = index == selectedIndex,
                onClick = { onSelect(index) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** One tab of [FinoteNavBar]. [label] is spoken by a screen reader. */
data class FinoteNavItem(
    @DrawableRes val iconRes: Int,
    val label: String,
)

@Composable
private fun NavTab(
    item: FinoteNavItem,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = FinoteTheme.scheme
    Box(
        modifier = modifier.fillMaxHeight().selectable(selected = selected, role = Role.Tab, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .size(width = FinoteTheme.sizes.navItemWidth, height = FinoteTheme.sizes.navItemHeight)
                    .clip(FinoteTheme.shapes.sm)
                    .background(if (selected) scheme.primary else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(item.iconRes),
                contentDescription = item.label,
                modifier = Modifier.size(FinoteTheme.sizes.iconMd),
                tint = if (selected) scheme.onPrimary else scheme.onSurfaceVariant,
            )
        }
    }
}

/** The four main destinations, for previews and screenshot tests. */
internal fun previewNavItems(): List<FinoteNavItem> =
    listOf(
        FinoteNavItem(R.drawable.ic_home, "Home"),
        FinoteNavItem(R.drawable.ic_receipt_long, "Transactions"),
        FinoteNavItem(R.drawable.ic_pie_chart, "Reports"),
        FinoteNavItem(R.drawable.ic_settings, "Settings"),
    )

@FinotePreviews
@Composable
private fun FinoteNavBarPreview() {
    FinotePreviewSurface {
        FinoteNavBar(items = previewNavItems(), selectedIndex = 0, onSelect = {})
    }
}
