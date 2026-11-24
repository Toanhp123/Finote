package io.github.toanhp123.finote.ui.transactions.component

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import io.github.toanhp123.finote.R
import io.github.toanhp123.finote.domain.account.model.Account
import io.github.toanhp123.finote.ui.cashentry.component.AmountDisplay
import io.github.toanhp123.finote.ui.cashentry.component.CashKeypad
import io.github.toanhp123.finote.ui.cashentry.logic.AmountExpression
import io.github.toanhp123.finote.ui.cashentry.logic.AmountResult
import io.github.toanhp123.finote.ui.cashentry.logic.CalcKey
import io.github.toanhp123.finote.ui.cashentry.logic.valueOrNull
import io.github.toanhp123.finote.ui.common.component.control.FinoteButton
import io.github.toanhp123.finote.ui.common.component.control.FinoteButtonStyle
import io.github.toanhp123.finote.ui.common.component.finance.MoneyText
import io.github.toanhp123.finote.ui.common.component.overlay.FinoteBottomSheet
import io.github.toanhp123.finote.ui.common.format.MoneyKind
import io.github.toanhp123.finote.ui.common.format.displayName
import io.github.toanhp123.finote.ui.common.rememberLastNonNull
import io.github.toanhp123.finote.ui.common.theme.FinoteTheme

/**
 * Asks for the real balance of [account] (null hides the sheet) and shows how far it is from the app's. The amount is
 * typed on the cash-entry keypad; Save sends it, the app never computes an adjustment by itself.
 */
@Composable
fun ReconcileSheet(
    account: Account?,
    onSubmit: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    // The sheet keeps its content while it slides out; the typed text is cleared once it is hidden.
    val shownAccount = rememberLastNonNull(account)
    var expression by rememberSaveable { mutableStateOf("") }
    FinoteBottomSheet(
        isVisible = account != null,
        onDismiss = onDismiss,
        onDismissComplete = { expression = "" },
    ) {
        shownAccount?.let {
            ReconcileContent(
                account = it,
                expression = expression,
                onKey = { key -> expression = AmountExpression.press(expression, key) },
                onSubmit = onSubmit,
                onDismiss = onDismiss,
            )
        }
    }
}

@Composable
private fun ColumnScope.ReconcileContent(
    account: Account,
    expression: String,
    onKey: (CalcKey) -> Unit,
    onSubmit: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val result = remember(expression) { AmountExpression.evaluate(expression) }
    // A real balance of zero is valid, so an unfinished result is not an error; only an empty field blocks Save.
    val actual = result.valueOrNull()
    Text(
        text = stringResource(R.string.reconcile_title),
        color = FinoteTheme.scheme.onSurface,
        style = FinoteTheme.typography.material.titleLarge,
        modifier = Modifier.semantics { heading() },
    )
    Text(
        text = displayName(account.name, account.nameKey),
        color = FinoteTheme.scheme.onSurfaceVariant,
        style = FinoteTheme.typography.material.bodyMedium,
    )
    Amounts(account.currentBalance, expression, result, actual)
    CashKeypad(onKey = onKey)
    Buttons(actual, onSubmit, onDismiss)
}

@Composable
private fun Amounts(
    appBalance: Long,
    expression: String,
    result: AmountResult,
    actual: Long?,
) {
    Column(
        modifier = Modifier.padding(vertical = FinoteTheme.spacing.md),
        verticalArrangement = Arrangement.spacedBy(FinoteTheme.spacing.sm),
    ) {
        LabeledMoney(R.string.reconcile_in_app, appBalance)
        Text(
            text = stringResource(R.string.reconcile_field),
            color = FinoteTheme.scheme.onSurfaceVariant,
            style = FinoteTheme.typography.material.labelMedium,
        )
        AmountDisplay(
            expression = expression,
            result = result,
            kind = MoneyKind.Neutral,
            verticalArrangement = Arrangement.spacedBy(FinoteTheme.spacing.sm),
        )
        differenceOf(actual, appBalance)?.let { LabeledMoney(R.string.reconcile_difference, it) }
    }
}

@Composable
private fun Buttons(
    actual: Long?,
    onSubmit: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = FinoteTheme.spacing.md),
        horizontalArrangement = Arrangement.spacedBy(FinoteTheme.spacing.sm),
    ) {
        FinoteButton(
            text = stringResource(R.string.action_cancel),
            onClick = onDismiss,
            modifier = Modifier.weight(1f),
            style = FinoteButtonStyle.Secondary,
        )
        FinoteButton(
            text = stringResource(R.string.cash_entry_save),
            onClick = { actual?.let(onSubmit) },
            modifier = Modifier.weight(1f),
            enabled = actual != null,
        )
    }
}

@Composable
private fun LabeledMoney(
    @StringRes labelRes: Int,
    amount: Long,
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(labelRes),
            modifier = Modifier.weight(1f),
            color = FinoteTheme.scheme.onSurfaceVariant,
            style = FinoteTheme.typography.material.bodyMedium,
        )
        MoneyText(amount = amount, kind = MoneyKind.Neutral, style = FinoteTheme.typography.material.bodyMedium)
    }
}

/** The signed gap between the typed and the app's balance; null with no typed amount or when it overflows Long. */
private fun differenceOf(
    actual: Long?,
    appBalance: Long,
): Long? {
    if (actual == null) return null
    val difference = actual - appBalance
    // The sign bits show an overflow: the operands differ in sign and the result's sign is not the minuend's.
    val overflowed = ((actual xor appBalance) and (actual xor difference)) < 0
    // formatMoney refuses Long.MIN_VALUE.
    return if (overflowed || difference == Long.MIN_VALUE) null else difference
}
