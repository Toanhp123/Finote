package io.github.toanhp123.finote.ui.cashentry.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.toanhp123.finote.R
import io.github.toanhp123.finote.ui.common.component.control.FinoteButton
import io.github.toanhp123.finote.ui.common.component.control.FinoteButtonStyle
import io.github.toanhp123.finote.ui.common.component.display.FinoteCard
import io.github.toanhp123.finote.ui.common.theme.FinoteTheme

/** Says that a balance adjustment already includes the notification, and offers to dismiss it from the inbox. */
@Composable
fun CoveredNotice(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    FinoteCard(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.cash_entry_covered_text),
            color = FinoteTheme.scheme.onSurface,
            style = FinoteTheme.typography.material.bodyMedium,
        )
        FinoteButton(
            text = stringResource(R.string.inbox_action_dismiss),
            onClick = onDismiss,
            style = FinoteButtonStyle.Secondary,
            enabled = enabled,
        )
    }
}
