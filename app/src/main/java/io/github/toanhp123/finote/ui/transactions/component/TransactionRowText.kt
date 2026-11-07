package io.github.toanhp123.finote.ui.transactions.component

import io.github.toanhp123.finote.domain.transaction.model.AdjustmentDirection
import io.github.toanhp123.finote.domain.transaction.model.ReimbursementStatus
import io.github.toanhp123.finote.domain.transaction.model.Transaction
import io.github.toanhp123.finote.domain.transaction.model.TransactionType
import io.github.toanhp123.finote.ui.common.format.MoneyKind
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

fun dayLabel(
    date: LocalDate,
    today: LocalDate,
    locale: Locale,
): DayLabel =
    when (date) {
        today -> {
            DayLabel.Today
        }

        today.minusDays(1) -> {
            DayLabel.Yesterday
        }

        else -> {
            val pattern = if (date.year == today.year) "EEE, dd/MM" else "EEE, dd/MM/yyyy"
            DayLabel.Date(DateTimeFormatter.ofPattern(pattern, locale).format(date))
        }
    }

/** An adjustment reads as income or expense by its direction, so it gets a sign and a color. */
fun moneyKindOf(transaction: Transaction): MoneyKind =
    when (transaction.type) {
        TransactionType.INCOME, TransactionType.REFUND -> {
            MoneyKind.Income
        }

        TransactionType.EXPENSE -> {
            MoneyKind.Expense
        }

        TransactionType.TRANSFER -> {
            MoneyKind.Transfer
        }

        TransactionType.ADJUSTMENT -> {
            if (transaction.adjustmentDirection == AdjustmentDirection.IN) MoneyKind.Income else MoneyKind.Expense
        }
    }

/**
 * [accountName] resolves an id to a display name, null when the account is gone. A reimbursement status adds its
 * label ([pendingLabel], [settledLabel]) after the accounts and before the note.
 */
fun rowSubtitle(
    transaction: Transaction,
    accountName: (String) -> String?,
    deletedLabel: String,
    pendingLabel: String,
    settledLabel: String,
): String {
    fun name(id: String) = accountName(id) ?: deletedLabel

    val accounts =
        if (transaction.type == TransactionType.TRANSFER) {
            "${name(transaction.accountId)} → ${name(checkNotNull(transaction.toAccountId))}"
        } else {
            name(transaction.accountId)
        }
    val label =
        when (transaction.reimbursementStatus) {
            ReimbursementStatus.NONE -> null
            ReimbursementStatus.PENDING -> pendingLabel
            ReimbursementStatus.SETTLED -> settledLabel
        }
    return listOfNotNull(accounts, label, transaction.note.takeIf { it.isNotBlank() }).joinToString(" · ")
}

/** What a day header says. Today and yesterday are words the screen takes from `strings.xml`. */
sealed interface DayLabel {
    data object Today : DayLabel

    data object Yesterday : DayLabel

    data class Date(
        val text: String,
    ) : DayLabel
}
