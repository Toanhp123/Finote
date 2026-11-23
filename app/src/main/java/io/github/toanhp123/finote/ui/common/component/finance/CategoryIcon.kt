package io.github.toanhp123.finote.ui.common.component.finance

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import io.github.toanhp123.finote.R
import io.github.toanhp123.finote.ui.common.component.preview.FinotePreviewSurface
import io.github.toanhp123.finote.ui.common.component.preview.FinotePreviews
import io.github.toanhp123.finote.ui.common.component.preview.PreviewGreenArgb
import io.github.toanhp123.finote.ui.common.component.preview.PreviewRedArgb
import io.github.toanhp123.finote.ui.common.theme.FinoteTheme
import io.github.toanhp123.finote.ui.common.theme.color.categoryIconColors

/** A tile tinted by the category color. Pass a null [contentDescription] when text next to it names the category. */
@Composable
fun CategoryIcon(
    @DrawableRes iconRes: Int,
    colorArgb: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    val tile = categoryIconColors(colorArgb, FinoteTheme.isDark)
    Box(
        modifier = modifier.size(FinoteTheme.sizes.avatar).clip(FinoteTheme.shapes.sm).background(tile.container),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            modifier = Modifier.size(FinoteTheme.sizes.iconSm),
            tint = tile.icon,
        )
    }
}

@FinotePreviews
@Composable
private fun CategoryIconPreview() {
    FinotePreviewSurface {
        Row {
            CategoryIcon(iconRes = R.drawable.ic_restaurant, colorArgb = PreviewRedArgb, contentDescription = null)
            CategoryIcon(iconRes = R.drawable.ic_work, colorArgb = PreviewGreenArgb, contentDescription = null)
            CategoryIcon(
                iconRes = R.drawable.ic_local_gas_station,
                colorArgb = PreviewRedArgb,
                contentDescription = null,
            )
        }
    }
}
