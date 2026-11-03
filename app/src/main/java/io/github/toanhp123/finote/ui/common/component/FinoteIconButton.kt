package io.github.toanhp123.finote.ui.common.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import io.github.toanhp123.finote.R
import io.github.toanhp123.finote.ui.common.theme.FinoteTheme

/** A round icon button with a touch target of the theme size. [contentDescription] names the action. */
@Composable
fun FinoteIconButton(
    @DrawableRes iconRes: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val onSurface = FinoteTheme.scheme.onSurface
    Box(
        modifier =
            modifier
                .size(FinoteTheme.sizes.touchTarget)
                .clip(FinoteTheme.shapes.full)
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            modifier = Modifier.size(FinoteTheme.sizes.iconMd),
            tint = if (enabled) onSurface else onSurface.copy(alpha = FinoteTheme.colors.disabledAlpha),
        )
    }
}

@FinotePreviews
@Composable
private fun FinoteIconButtonPreview() {
    FinotePreviewSurface {
        Row(horizontalArrangement = Arrangement.spacedBy(FinoteTheme.spacing.sm)) {
            FinoteIconButton(iconRes = R.drawable.ic_add, contentDescription = "Add", onClick = {})
            FinoteIconButton(iconRes = R.drawable.ic_add, contentDescription = "Add", onClick = {}, enabled = false)
        }
    }
}
