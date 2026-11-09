package io.github.toanhp123.finote.ui.transactions.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import io.github.toanhp123.finote.R
import io.github.toanhp123.finote.ui.common.component.control.FinoteButton
import io.github.toanhp123.finote.ui.common.component.control.FinoteButtonStyle
import io.github.toanhp123.finote.ui.common.component.display.FinoteCard
import io.github.toanhp123.finote.ui.common.format.MoneyKind
import io.github.toanhp123.finote.ui.common.format.formatMoney
import io.github.toanhp123.finote.ui.common.theme.FinoteTheme

/** Says the account's balance differs from the bank's by [difference] (signed VND). [onAdjust] books the gap. */
@Composable
fun BalanceDriftBanner(
    accountName: String,
    difference: Long,
    onAdjust: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val amount = formatMoney(difference, MoneyKind.Neutral, LocalConfiguration.current.locales[0])
    val adjustDescription = stringResource(R.string.balance_drift_adjust_cd, accountName)
    FinoteCard(modifier = modifier.fillMaxWidth()) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(FinoteTheme.spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.balance_drift_message, accountName, amount),
                modifier = Modifier.weight(1f),
                color = FinoteTheme.scheme.onSurface,
                style = FinoteTheme.typography.material.bodyMedium,
            )
            FinoteButton(
                text = stringResource(R.string.balance_drift_adjust),
                onClick = onAdjust,
                modifier = Modifier.semantics { contentDescription = adjustDescription },
                style = FinoteButtonStyle.Secondary,
            )
        }
    }
}
