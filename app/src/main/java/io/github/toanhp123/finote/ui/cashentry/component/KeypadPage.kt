package io.github.toanhp123.finote.ui.cashentry.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.toanhp123.finote.R
import io.github.toanhp123.finote.domain.transaction.model.TransactionType
import io.github.toanhp123.finote.ui.cashentry.CashEntryIntent
import io.github.toanhp123.finote.ui.cashentry.CashEntryPage
import io.github.toanhp123.finote.ui.cashentry.CashEntryState
import io.github.toanhp123.finote.ui.cashentry.logic.AmountResult
import io.github.toanhp123.finote.ui.common.component.control.FinoteButton
import io.github.toanhp123.finote.ui.common.component.control.FinoteButtonStyle
import io.github.toanhp123.finote.ui.common.format.MoneyKind
import io.github.toanhp123.finote.ui.common.theme.FinoteTheme

@Composable
fun KeypadPage(
    state: CashEntryState,
    onIntent: (CashEntryIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(FinoteTheme.spacing.md)) {
        state.sourceText?.let { SourceNotificationBlock(it) }
        TypeRow(
            type = state.type,
            onTypeChange = { onIntent(CashEntryIntent.TypeChanged(it)) },
            isEditing = state.isEditing,
            isSaving = state.isSaving,
            onDelete = { onIntent(CashEntryIntent.DeleteClicked) },
            isTransferAllowed = state.sourceText == null,
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
        if (state.selectedAccountId == null && state.archivedAccountName != null) {
            Text(
                text = stringResource(R.string.cash_entry_archived_account, state.archivedAccountName),
                color = FinoteTheme.scheme.onSurfaceVariant,
                style = FinoteTheme.typography.material.bodySmall,
            )
        }
        if (state.isEditing && state.type == TransactionType.EXPENSE) {
            ReimbursementRow(
                selected = state.reimbursement,
                onPick = { onIntent(CashEntryIntent.ReimbursementPicked(it)) },
            )
        }
        CashKeypad(onKey = { onIntent(CashEntryIntent.KeyPressed(it)) })
        if (state.isCoveredNotice) {
            CoveredNotice(onDismiss = { onIntent(CashEntryIntent.DismissFromCovered) }, enabled = !state.isSaving)
        }
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
    // An unfinished (not positive) result must not look like an amount the user can save.
    val kind =
        when {
            state.result is AmountResult.NotPositive -> MoneyKind.Neutral
            state.type == TransactionType.INCOME -> MoneyKind.Income
            state.type == TransactionType.TRANSFER -> MoneyKind.Neutral
            else -> MoneyKind.Expense
        }
    AmountDisplay(
        expression = state.expression,
        result = state.result,
        kind = kind,
        modifier = modifier,
        hasSaveError = state.hasSaveError,
    )
}
