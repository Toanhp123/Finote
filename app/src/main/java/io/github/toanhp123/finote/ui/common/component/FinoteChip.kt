package io.github.toanhp123.finote.ui.common.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import io.github.toanhp123.finote.R
import io.github.toanhp123.finote.ui.common.theme.FinoteTheme

/** A pill for a filter or a choice. The touch target is the theme size even though the pill itself is lower. */
@Composable
fun FinoteChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes iconRes: Int? = null,
) {
    val contentColor = if (selected) FinoteTheme.scheme.onPrimary else FinoteTheme.scheme.onSurface
    Box(
        modifier =
            modifier
                .heightIn(min = FinoteTheme.sizes.touchTarget)
                .selectable(selected = selected, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier =
                Modifier
                    .clip(FinoteTheme.shapes.full)
                    .background(if (selected) FinoteTheme.scheme.primary else FinoteTheme.colors.card)
                    .border(FinoteTheme.sizes.border, FinoteTheme.colors.glass.stroke, FinoteTheme.shapes.full)
                    .padding(horizontal = FinoteTheme.spacing.md, vertical = FinoteTheme.spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(FinoteTheme.spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (iconRes != null) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(FinoteTheme.sizes.iconSm),
                    tint = contentColor,
                )
            }
            Text(text = text, color = contentColor, style = FinoteTheme.typography.material.labelLarge)
        }
    }
}

@FinotePreviews
@Composable
private fun FinoteChipPreview() {
    FinotePreviewSurface {
        Row(horizontalArrangement = Arrangement.spacedBy(FinoteTheme.spacing.sm)) {
            FinoteChip(text = "Food", selected = true, onClick = {}, iconRes = R.drawable.ic_restaurant)
            FinoteChip(text = "Work", selected = false, onClick = {}, iconRes = R.drawable.ic_work)
            FinoteChip(text = "All", selected = false, onClick = {})
        }
    }
}
