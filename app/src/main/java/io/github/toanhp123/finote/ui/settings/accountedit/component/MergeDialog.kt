package io.github.toanhp123.finote.ui.settings.accountedit.component

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import io.github.toanhp123.finote.R
import io.github.toanhp123.finote.domain.account.model.Account
import io.github.toanhp123.finote.ui.common.component.display.FinoteListRow
import io.github.toanhp123.finote.ui.common.component.finance.MoneyText
import io.github.toanhp123.finote.ui.common.component.overlay.FinoteDialog
import io.github.toanhp123.finote.ui.common.format.MoneyKind
import io.github.toanhp123.finote.ui.common.format.displayName
import io.github.toanhp123.finote.ui.common.format.formatMoney
import io.github.toanhp123.finote.ui.settings.accountedit.AccountEditDialog
import io.github.toanhp123.finote.ui.settings.accountedit.AccountEditIntent

@Composable
fun MergeDialog(
    dialog: AccountEditDialog.Merge,
    isVisible: Boolean,
    onIntent: (AccountEditIntent) -> Unit,
) {
    val (source, target, count) = dialog
    FinoteDialog(
        isVisible = isVisible,
        title =
            stringResource(
                R.string.accounts_merge_dialog_title,
                displayName(source.name, source.nameKey),
                displayName(target.name, target.nameKey),
            ),
        text = mergeText(source, count, dialog.droppedStart),
        onDismiss = { onIntent(AccountEditIntent.DialogDismissed) },
        confirmText = stringResource(R.string.accounts_merge_confirm),
        onConfirm = { onIntent(AccountEditIntent.MergeConfirmed) },
        isDestructive = true,
        content = { MergeBalances(source, target) },
    )
}

/** The move count, plus the warning when the merge drops the source's start balance. */
@Composable
private fun mergeText(
    source: Account,
    count: Int,
    droppedStart: Long?,
): String {
    val moves = pluralStringResource(R.plurals.accounts_merge_dialog_text, count, count)
    if (droppedStart == null) return moves
    val amount = formatMoney(droppedStart, MoneyKind.Neutral, LocalConfiguration.current.locales[0])
    val warning =
        stringResource(R.string.accounts_merge_drops_start, amount, displayName(source.name, source.nameKey))
    return "$moves\n\n$warning"
}

/** Both balances, so the user sees what each side holds before the merge. */
@Composable
private fun MergeBalances(
    source: Account,
    target: Account,
) {
    Column {
        listOf(source, target).forEach { account ->
            FinoteListRow(
                title = displayName(account.name, account.nameKey),
                trailing = { MoneyText(amount = account.currentBalance, kind = MoneyKind.Neutral) },
            )
        }
    }
}
