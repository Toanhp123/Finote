package io.github.toanhp123.finote.ui.budget.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.toanhp123.finote.R
import io.github.toanhp123.finote.ui.common.component.control.FinoteButton
import io.github.toanhp123.finote.ui.common.component.control.FinoteButtonStyle
import io.github.toanhp123.finote.ui.common.component.display.FinoteCard
import io.github.toanhp123.finote.ui.common.theme.FinoteTheme

/** Tells the user that budget alerts are off. [onEnable] asks again or opens the system settings. */
@Composable
fun NotificationOffBanner(
    onEnable: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FinoteCard(modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(FinoteTheme.spacing.md),
        ) {
            Text(
                text = stringResource(R.string.budget_alerts_off),
                modifier = Modifier.weight(1f),
                color = FinoteTheme.scheme.onSurface,
                style = FinoteTheme.typography.material.bodyMedium,
            )
            FinoteButton(
                text = stringResource(R.string.budget_alerts_on),
                onClick = onEnable,
                style = FinoteButtonStyle.Secondary,
            )
        }
    }
}
