package io.github.toanhp123.finote.ui.cashentry.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import io.github.toanhp123.finote.R
import io.github.toanhp123.finote.domain.transaction.model.TransactionType
import io.github.toanhp123.finote.ui.cashentry.CashEntryIntent
import io.github.toanhp123.finote.ui.cashentry.CashEntryPage
import io.github.toanhp123.finote.ui.cashentry.CashEntryState
import io.github.toanhp123.finote.ui.cashentry.logic.AmountResult
import io.github.toanhp123.finote.ui.cashentry.logic.InvalidReason
import io.github.toanhp123.finote.ui.common.component.control.FinoteButton
import io.github.toanhp123.finote.ui.common.component.control.FinoteButtonStyle
import io.github.toanhp123.finote.ui.common.component.finance.MoneyText
import io.github.toanhp123.finote.ui.common.format.MoneyKind
import io.github.toanhp123.finote.ui.common.theme.FinoteTheme

@Composable
fun KeypadPage(
    state: CashEntryState,
    onIntent: (CashEntryIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(FinoteTheme.spacing.md)) {
        TypeRow(
            type = state.type,
            onTypeChange = { onIntent(CashEntryIntent.TypeChanged(it)) },
            isEditing = state.isEditing,
            isSaving = state.isSaving,
            onDelete = { onIntent(CashEntryIntent.DeleteClicked) },
        )
        if (state.isEditing && state.isAutoPaired) {
            FinoteButton(
                text = stringResource(R.string.cash_entry_unpair),
                onClick = { onIntent(CashEntryIntent.UnpairClicked) },
                modifier = Modifier.fillMaxWidth(),
                style = FinoteButtonStyle.Secondary,
                enabled = !state.isSaving,
            )
        }
        AmountHeader(state)
        Selectors(state, onIntent, onDateClick = { onIntent(CashEntryIntent.PageOpened(CashEntryPage.DATE)) })
        if (state.isEditing && state.type == TransactionType.EXPENSE) {
            ReimbursementRow(
                selected = state.reimbursement,
                onPick = { onIntent(CashEntryIntent.ReimbursementPicked(it)) },
            )
        }
        CashKeypad(onKey = { onIntent(CashEntryIntent.KeyPressed(it)) })
        FinoteButton(
            text = stringResource(R.string.cash_entry_save),
            onClick = { onIntent(CashEntryIntent.SaveClicked) },
            modifier = Modifier.fillMaxWidth(),
            enabled = state.isSaveEnabled,
            isLoading = state.isSaving,
        )
    }
}

@Composable
private fun AmountHeader(
    state: CashEntryState,
    modifier: Modifier = Modifier,
) {
    val amount =
        when (val result = state.result) {
            is AmountResult.Ok -> result.amount
            is AmountResult.NotPositive -> result.value
            else -> 0L
        }
    // An unfinished (not positive) result must not look like an amount the user can save.
    val kind =
        when {
            state.result is AmountResult.NotPositive -> MoneyKind.Neutral
            state.type == TransactionType.INCOME -> MoneyKind.Income
            state.type == TransactionType.TRANSFER -> MoneyKind.Neutral
            else -> MoneyKind.Expense
        }
    val error = errorMessage(state)
    Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
        Text(
            text = state.expression,
            color = FinoteTheme.scheme.onSurfaceVariant,
            style = FinoteTheme.typography.material.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.StartEllipsis,
            textAlign = TextAlign.End,
            modifier = Modifier.fillMaxWidth(),
            minLines = 1,
        )
        MoneyText(
            amount = amount,
            kind = kind,
            style = FinoteTheme.typography.amountLarge,
            shrinkToFit = true,
        )
        // Always one line tall, so the keypad does not jump when an error appears.
        Text(
            text = error,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            color = FinoteTheme.colors.expense.color,
            style = FinoteTheme.typography.material.bodySmall,
            minLines = 1,
        )
    }
}

/** Only an invalid calculation or a failed save shows text; a result that is not positive is just unfinished. */
@Composable
private fun errorMessage(state: CashEntryState): String {
    val result = state.result
    val res =
        when {
            result is AmountResult.Invalid && result.reason == InvalidReason.DIVIDE_BY_ZERO -> {
                R.string.cash_entry_error_divide_by_zero
            }

            result is AmountResult.Invalid -> {
                R.string.cash_entry_error_overflow
            }

            state.hasSaveError -> {
                R.string.cash_entry_error_save
            }

            else -> {
                return ""
            }
        }
    return stringResource(res)
}
