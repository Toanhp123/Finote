package io.github.toanhp123.finote.ui.transactions.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import io.github.toanhp123.finote.R
import io.github.toanhp123.finote.ui.common.component.control.FinoteIconButton
import io.github.toanhp123.finote.ui.common.component.display.FinoteCard
import io.github.toanhp123.finote.ui.common.theme.FinoteTheme

/**
 * Says [count] notifications could not be recorded. A tap on the card calls [onReview]; the close button calls
 * [onClose] and hides the banner until a new item arrives.
 */
@Composable
fun FailedInboxBanner(
    count: Int,
    onReview: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FinoteCard(modifier = modifier.fillMaxWidth(), onClick = onReview) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(FinoteTheme.spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = pluralStringResource(R.plurals.inbox_banner_message, count, count),
                modifier = Modifier.weight(1f),
                color = FinoteTheme.scheme.onSurface,
                style = FinoteTheme.typography.material.bodyMedium,
            )
            FinoteIconButton(
                iconRes = R.drawable.ic_close,
                contentDescription = stringResource(R.string.inbox_banner_close),
                onClick = onClose,
            )
        }
    }
}
