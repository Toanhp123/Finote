package io.github.toanhp123.finote.ui.cashentry.logic

import io.github.toanhp123.finote.ui.cashentry.CashEntryPage
import io.github.toanhp123.finote.ui.cashentry.CashEntryState

internal fun CashEntryState.backToKeypad() = copy(page = CashEntryPage.KEYPAD)

/** Both confirm dialogs of edit mode are closed. */
internal fun CashEntryState.withoutConfirms() = copy(isDeleteConfirmOpen = false, isUnpairConfirmOpen = false)

/** A save, delete or unpair runs: the sheet is busy and an earlier save error is cleared. */
internal fun CashEntryState.busy() = copy(isSaving = true, saveError = null)

internal fun CashEntryState.afterKey(key: CalcKey): CashEntryState {
    val next = AmountExpression.press(expression, key)
    return copy(expression = next, result = AmountExpression.evaluate(next), saveError = null)
}

/** An id that is not in the live list (for example archived while the page was open) changes nothing. */
internal fun CashEntryState.withAccount(id: String): CashEntryState =
    // A destination equal to the new source is cleared, so the two never match.
    if (accounts.any { it.id == id }) {
        copy(selectedAccountId = id, destinationId = destinationId?.takeIf { it != id }).backToKeypad()
    } else {
        this
    }

/** Only a live account other than the source counts; anything else changes nothing. */
internal fun CashEntryState.withDestination(id: String): CashEntryState =
    if (destinationChoices.any { it.id == id }) copy(destinationId = id).backToKeypad() else this

internal fun CashEntryState.withCategory(id: String?): CashEntryState =
    copy(selectedCategoryIds = selectedCategoryIds + (type to id)).backToKeypad()

/** Digits only, at most MAX_DIGITS: the fee fits a Long, and the use case checks amount + fee. */
internal fun feeOf(text: String): Long =
    text
        .filter { it in '0'..'9' }
        .take(AmountExpression.MAX_DIGITS)
        .toLongOrNull() ?: 0L
