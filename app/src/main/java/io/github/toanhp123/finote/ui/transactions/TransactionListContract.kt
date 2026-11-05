package io.github.toanhp123.finote.ui.transactions

import io.github.toanhp123.finote.domain.account.model.Account
import io.github.toanhp123.finote.domain.category.model.Category
import io.github.toanhp123.finote.domain.transaction.model.TransactionDay
import java.time.LocalDate

/**
 * [accounts] are the filter chips: not archived, by sortOrder. [accountsById] also holds archived accounts, so a row
 * can still show their name. A null [selectedAccountId] means every account. [isPendingOnly] shows the rows awaiting
 * repayment of every account, so it excludes an account choice. [editingId] is the expense whose sheet is open.
 */
data class TransactionListState(
    val isLoading: Boolean = true,
    /** The ViewModel clock's date; the screen never reads a clock. */
    val today: LocalDate,
    val hasError: Boolean = false,
    val accounts: List<Account> = emptyList(),
    val selectedAccountId: String? = null,
    val days: List<TransactionDay> = emptyList(),
    val accountsById: Map<String, Account> = emptyMap(),
    val categoriesById: Map<String, Category> = emptyMap(),
    val isPendingOnly: Boolean = false,
    val editingId: String? = null,
    val hasSaveError: Boolean = false,
)

sealed interface TransactionListIntent {
    data class SelectAccount(
        val accountId: String?,
    ) : TransactionListIntent

    data object SelectPending : TransactionListIntent

    data class RowClicked(
        val id: String,
    ) : TransactionListIntent

    data class ReimbursementPicked(
        val choice: ReimbursementChoice,
    ) : TransactionListIntent

    data object SheetDismissed : TransactionListIntent
}

/** This screen sends no one-time command; the type exists for the contract rule and [BaseViewModel]. */
sealed interface TransactionListEffect
