package io.github.toanhp123.finote.ui.common.component.display

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import io.github.toanhp123.finote.R
import io.github.toanhp123.finote.ui.common.component.control.FinoteButton
import io.github.toanhp123.finote.ui.common.component.preview.FinotePreviewSurface
import io.github.toanhp123.finote.ui.common.component.preview.FinotePreviews
import io.github.toanhp123.finote.ui.common.theme.FinoteTheme

/** A centered icon, [title], optional [message] and optional [action], for a list with nothing to show. */
@Composable
fun FinoteEmptyState(
    @DrawableRes iconRes: Int,
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    action: (@Composable () -> Unit)? = null,
) {
    val muted = FinoteTheme.scheme.onSurfaceVariant
    Column(
        modifier = modifier.fillMaxWidth().padding(FinoteTheme.spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(FinoteTheme.spacing.sm, Alignment.CenterVertically),
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.size(FinoteTheme.sizes.emptyIcon),
            tint = muted,
        )
        Text(
            text = title,
            color = FinoteTheme.scheme.onSurface,
            style = FinoteTheme.typography.material.titleMedium,
            textAlign = TextAlign.Center,
        )
        if (message != null) {
            Text(
                text = message,
                color = muted,
                style = FinoteTheme.typography.material.bodyMedium,
                textAlign = TextAlign.Center,
            )
        }
        if (action != null) action()
    }
}

@FinotePreviews
@Composable
private fun FinoteEmptyStatePreview() {
    FinotePreviewSurface {
        FinoteEmptyState(
            iconRes = R.drawable.ic_receipt_long,
            title = "No transactions yet",
            message = "Add one or let Finote read your bank notifications.",
            action = { FinoteButton(text = "Add", onClick = {}, leadingIcon = R.drawable.ic_add) },
        )
    }
}
