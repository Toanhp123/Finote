package io.github.toanhp123.finote.ui.cashentry.component

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import io.github.toanhp123.finote.R
import io.github.toanhp123.finote.ui.common.component.display.FinoteListRow
import io.github.toanhp123.finote.ui.common.component.finance.CategoryIcon
import io.github.toanhp123.finote.ui.common.theme.FinoteTheme

@Composable
fun PickerRow(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: Int? = null,
    colorArgb: Int = 0,
) {
    FinoteListRow(
        title = title,
        modifier = modifier.semantics { selected = isSelected },
        leading = icon?.let { { CategoryIcon(iconRes = it, colorArgb = colorArgb, contentDescription = null) } },
        // The check is an icon, so the selection does not rely on color alone.
        trailing =
            if (isSelected) {
                {
                    Icon(
                        painter = painterResource(R.drawable.ic_check),
                        contentDescription = null,
                        modifier = Modifier.size(FinoteTheme.sizes.iconMd),
                        tint = FinoteTheme.scheme.primary,
                    )
                }
            } else {
                null
            },
        onClick = onClick,
    )
}
