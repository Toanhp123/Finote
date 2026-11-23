package io.github.toanhp123.finote.ui.transactions.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import io.github.toanhp123.finote.R
import io.github.toanhp123.finote.ui.common.component.control.FinoteButton
import io.github.toanhp123.finote.ui.common.component.control.FinoteButtonStyle
import io.github.toanhp123.finote.ui.common.component.display.FinoteCard
import io.github.toanhp123.finote.ui.common.theme.FinoteTheme

/**
 * Says the account's balance may differ from the bank's, without an amount. [onReconcile] asks for the real balance.
 * [otherCount] other accounts drift too; above zero the banner says so under the message.
 */
@Composable
fun BalanceDriftBanner(
    accountName: String,
    onReconcile: () -> Unit,
    modifier: Modifier = Modifier,
    otherCount: Int = 0,
) {
    val reconcileDescription = stringResource(R.string.balance_drift_adjust_cd, accountName)
    FinoteCard(modifier = modifier.fillMaxWidth()) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(FinoteTheme.spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.balance_drift_message, accountName),
                    color = FinoteTheme.scheme.onSurface,
                    style = FinoteTheme.typography.material.bodyMedium,
                )
                if (otherCount > 0) {
                    Text(
                        text = pluralStringResource(R.plurals.balance_drift_others, otherCount, otherCount),
                        color = FinoteTheme.scheme.onSurfaceVariant,
                        style = FinoteTheme.typography.material.bodySmall,
                    )
                }
            }
            FinoteButton(
                text = stringResource(R.string.balance_drift_adjust),
                onClick = onReconcile,
                modifier = Modifier.semantics { contentDescription = reconcileDescription },
                style = FinoteButtonStyle.Secondary,
            )
        }
    }
}
