package io.github.toanhp123.finote.ui.cashentry

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.toanhp123.finote.R
import io.github.toanhp123.finote.domain.transaction.AmountResult
import io.github.toanhp123.finote.domain.transaction.InvalidReason
import io.github.toanhp123.finote.domain.transaction.model.TransactionType
import io.github.toanhp123.finote.ui.common.ObserveEffects
import io.github.toanhp123.finote.ui.common.component.FinoteBottomSheet
import io.github.toanhp123.finote.ui.common.component.FinoteButton
import io.github.toanhp123.finote.ui.common.component.FinoteDatePicker
import io.github.toanhp123.finote.ui.common.component.FinoteSegmented
import io.github.toanhp123.finote.ui.common.component.MoneyText
import io.github.toanhp123.finote.ui.common.format.MoneyKind
import io.github.toanhp123.finote.ui.common.theme.FinoteTheme
import kotlinx.coroutines.delay

// FinoteTheme.motion.emphasized is a spring with no fixed duration, so wait a fixed time for the sheet to leave.
private const val SHEET_EXIT_MILLIS = 300L
private val TypeOptions = listOf(TransactionType.EXPENSE, TransactionType.INCOME)

@Composable
fun CashEntryRoute(
    viewModel: CashEntryViewModel,
    onClose: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var isVisible by remember { mutableStateOf(true) }
    // A user dismiss has already moved the sheet away, so there is nothing to wait for.
    var isDismissedByUser by remember { mutableStateOf(false) }
    val currentOnClose by rememberUpdatedState(onClose)
    ObserveEffects(viewModel.effects) { effect ->
        when (effect) {
            CashEntryEffect.Close -> isVisible = false
        }
    }
    LaunchedEffect(isVisible) {
        if (!isVisible) {
            if (!isDismissedByUser) delay(SHEET_EXIT_MILLIS)
            currentOnClose()
        }
    }
    FinoteBottomSheet(
        isVisible = isVisible,
        onDismiss = {
            isDismissedByUser = true
            viewModel.onIntent(CashEntryIntent.Dismissed)
        },
    ) {
        BackHandler(enabled = state.page != CashEntryPage.KEYPAD) { viewModel.onIntent(CashEntryIntent.PageClosed) }
        CashEntrySheet(state = state, onIntent = viewModel::onIntent)
    }
}

/** The content inside the sheet. It has no ViewModel, so previews and screenshot tests can draw it. */
@Composable
fun CashEntrySheet(
    state: CashEntryState,
    onIntent: (CashEntryIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state.page) {
        CashEntryPage.KEYPAD -> {
            KeypadPage(state, onIntent, modifier)
        }

        CashEntryPage.ACCOUNT -> {
            AccountList(
                accounts = state.accounts,
                selectedId = state.selectedAccountId,
                onPick = { onIntent(CashEntryIntent.AccountPicked(it)) },
                modifier = modifier,
            )
        }

        CashEntryPage.CATEGORY -> {
            CategoryList(
                categories = state.categories,
                selectedId = state.selectedCategoryIds[state.type],
                onPick = { onIntent(CashEntryIntent.CategoryPicked(it)) },
                modifier = modifier,
            )
        }

        CashEntryPage.NOTE -> {
            NotePage(
                note = state.note,
                onNoteChange = { onIntent(CashEntryIntent.NoteChanged(it)) },
                onDone = { onIntent(CashEntryIntent.PageClosed) },
                modifier = modifier,
            )
        }
    }
}

@Composable
private fun KeypadPage(
    state: CashEntryState,
    onIntent: (CashEntryIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    var isDatePickerVisible by rememberSaveable { mutableStateOf(false) }
    val expense = stringResource(R.string.cash_entry_expense)
    val income = stringResource(R.string.cash_entry_income)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(FinoteTheme.spacing.md)) {
        FinoteSegmented(
            options = TypeOptions,
            selected = state.type,
            onSelect = { onIntent(CashEntryIntent.TypeChanged(it)) },
            label = { if (it == TransactionType.INCOME) income else expense },
            modifier = Modifier.fillMaxWidth(),
        )
        AmountHeader(state)
        Selectors(state, onIntent, onDateClick = { isDatePickerVisible = true })
        CashKeypad(onKey = { onIntent(CashEntryIntent.KeyPressed(it)) })
        FinoteButton(
            text = stringResource(R.string.cash_entry_save),
            onClick = { onIntent(CashEntryIntent.SaveClicked) },
            modifier = Modifier.fillMaxWidth(),
            enabled = state.isSaveEnabled,
            isLoading = state.isSaving,
        )
    }
    FinoteDatePicker(
        isVisible = isDatePickerVisible,
        selected = state.date,
        onConfirm = {
            onIntent(CashEntryIntent.DatePicked(it))
            isDatePickerVisible = false
        },
        onDismiss = { isDatePickerVisible = false },
    )
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
