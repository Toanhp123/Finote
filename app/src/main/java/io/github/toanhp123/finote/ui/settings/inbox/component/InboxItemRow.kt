package io.github.toanhp123.finote.ui.settings.inbox.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import io.github.toanhp123.finote.R
import io.github.toanhp123.finote.domain.notification.model.FailedInboxItem
import io.github.toanhp123.finote.domain.transaction.model.TransactionType
import io.github.toanhp123.finote.ui.common.component.control.FinoteButton
import io.github.toanhp123.finote.ui.common.component.control.FinoteButtonStyle
import io.github.toanhp123.finote.ui.common.component.display.FinoteCard
import io.github.toanhp123.finote.ui.common.component.finance.MoneyText
import io.github.toanhp123.finote.ui.common.format.MoneyKind
import io.github.toanhp123.finote.ui.common.format.formatShortDateTime
import io.github.toanhp123.finote.ui.common.theme.FinoteTheme
import io.github.toanhp123.finote.ui.settings.inbox.logic.inboxActionsOf
import java.time.ZoneId

/**
 * One inbox item: bank name, the amount when a rule read one, the title and the time. Tapping the card books it.
 * The buttons are the actions [inboxActionsOf] allows for [item].
 */
@Composable
fun InboxItemRow(
    item: FailedInboxItem,
    bankName: String,
    onBook: () -> Unit,
    onDismiss: () -> Unit,
    onAd: () -> Unit,
    onRestore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val actions = inboxActionsOf(item)
    val locale = LocalConfiguration.current.locales[0]
    val time = remember(item.postTime, locale) { formatShortDateTime(item.postTime, ZoneId.systemDefault(), locale) }
    val muted = FinoteTheme.scheme.onSurfaceVariant
    FinoteCard(modifier = modifier.fillMaxWidth(), onClick = if (actions.isBookable) onBook else null) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(FinoteTheme.spacing.md),
        ) {
            Text(
                text = bankName,
                modifier = Modifier.weight(1f),
                color = muted,
                style = FinoteTheme.typography.material.labelLarge,
            )
            item.parsed?.takeIf { it.amount > 0 }?.let { MoneyText(amount = it.amount, kind = moneyKindOf(it.type)) }
        }
        Text(
            text = item.title.ifBlank { item.content },
            color = FinoteTheme.scheme.onSurface,
            style = FinoteTheme.typography.material.bodyLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(text = time, color = muted, style = FinoteTheme.typography.material.bodySmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(FinoteTheme.spacing.sm)) {
            if (actions.isDismissible) {
                FinoteButton(
                    text = stringResource(R.string.inbox_action_dismiss),
                    onClick = onDismiss,
                    style = FinoteButtonStyle.Ghost,
                )
            }
            if (actions.isAdCandidate) {
                FinoteButton(
                    text = stringResource(R.string.inbox_action_ad),
                    onClick = onAd,
                    style = FinoteButtonStyle.Ghost,
                )
            }
            if (actions.isRestorable) {
                FinoteButton(
                    text = stringResource(R.string.inbox_action_restore),
                    onClick = onRestore,
                    style = FinoteButtonStyle.Secondary,
                )
            }
        }
    }
}

private fun moneyKindOf(type: TransactionType) =
    when (type) {
        TransactionType.INCOME, TransactionType.REFUND -> MoneyKind.Income
        TransactionType.EXPENSE -> MoneyKind.Expense
        TransactionType.TRANSFER -> MoneyKind.Transfer
        TransactionType.ADJUSTMENT -> MoneyKind.Neutral
    }
