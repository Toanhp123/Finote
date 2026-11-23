package io.github.toanhp123.finote.ui.transactions

import io.github.toanhp123.finote.domain.account.model.Account
import io.github.toanhp123.finote.domain.account.model.BalanceDrift
import io.github.toanhp123.finote.domain.category.model.Category
import io.github.toanhp123.finote.domain.transaction.model.TransactionDay
import java.time.LocalDate

/**
 * [accounts] are the filter chips: not archived or ignored, by sortOrder. [accountsById] also holds those accounts,
 * so a row can still show their name. A null [selectedAccountId] means every account. [isPendingOnly] shows the rows
 * awaiting repayment of every account, so it excludes an account choice.
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
    /** Accounts whose balance may differ from the bank's; the first shows a banner that opens the reconcile sheet. */
    val drifts: List<BalanceDrift> = emptyList(),
    /** The account whose real balance the user is typing; null shows no sheet. */
    val reconcilingAccountId: String? = null,
    /** The balance adjustment waiting for the user to confirm its deletion; null shows no dialog. */
    val deletingId: String? = null,
    /** Ids of the transfers made by auto-pairing; their row says so. */
    val autoPairedIds: Set<String> = emptySet(),
)

sealed interface TransactionListIntent {
    data class SelectAccount(
        val accountId: String?,
    ) : TransactionListIntent

    data object SelectPending : TransactionListIntent

    data class RowClicked(
        val id: String,
    ) : TransactionListIntent

    data class ReconcileClicked(
        val accountId: String,
    ) : TransactionListIntent

    data class ReconcileSubmitted(
        val actualBalance: Long,
    ) : TransactionListIntent

    data object ReconcileDismissed : TransactionListIntent

    data object DeleteConfirmed : TransactionListIntent

    data object DeleteDismissed : TransactionListIntent
}

sealed interface TransactionListEffect {
    data class OpenEditor(
        val id: String,
    ) : TransactionListEffect

    data object DeleteFailed : TransactionListEffect

    data object ReconcileFailed : TransactionListEffect
}
