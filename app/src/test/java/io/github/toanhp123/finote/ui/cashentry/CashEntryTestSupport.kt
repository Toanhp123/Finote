package io.github.toanhp123.finote.ui.cashentry

import io.github.toanhp123.finote.domain.account.usecase.FakeAccountRepository
import io.github.toanhp123.finote.domain.account.usecase.ObserveAccountsUseCase
import io.github.toanhp123.finote.domain.account.usecase.ObserveUsableAccountsUseCase
import io.github.toanhp123.finote.domain.category.usecase.FakeCategoryRepository
import io.github.toanhp123.finote.domain.category.usecase.ObserveCategoriesUseCase
import io.github.toanhp123.finote.domain.common.FakeTransactionRunner
import io.github.toanhp123.finote.domain.notification.repository.FailedInboxRepository
import io.github.toanhp123.finote.domain.notification.usecase.BookInboxItemUseCase
import io.github.toanhp123.finote.domain.notification.usecase.DismissInboxItemUseCase
import io.github.toanhp123.finote.domain.notification.usecase.FakeFailedInboxRepository
import io.github.toanhp123.finote.domain.notification.usecase.GetInboxItemUseCase
import io.github.toanhp123.finote.domain.notification.usecase.GetInboxPrefillUseCase
import io.github.toanhp123.finote.domain.transaction.repository.TransactionRepository
import io.github.toanhp123.finote.domain.transaction.repository.TransferPairRepository
import io.github.toanhp123.finote.domain.transaction.usecase.AddManualTransactionUseCase
import io.github.toanhp123.finote.domain.transaction.usecase.AddTransactionUseCase
import io.github.toanhp123.finote.domain.transaction.usecase.DeleteTransactionUseCase
import io.github.toanhp123.finote.domain.transaction.usecase.FakeTransactionRepository
import io.github.toanhp123.finote.domain.transaction.usecase.FakeTransferPairRepository
import io.github.toanhp123.finote.domain.transaction.usecase.GetLastManualChoicesUseCase
import io.github.toanhp123.finote.domain.transaction.usecase.GetTransactionUseCase
import io.github.toanhp123.finote.domain.transaction.usecase.IsAutoPairedUseCase
import io.github.toanhp123.finote.domain.transaction.usecase.UnpairTransferUseCase
import io.github.toanhp123.finote.domain.transaction.usecase.UpdateTransactionUseCase
import java.time.Clock

/** A [CashEntryViewModel] over fakes, as Hilt would build it. [transactionId] null adds, otherwise edits that row. */
internal fun cashEntryViewModel(
    transactionId: String?,
    accounts: FakeAccountRepository,
    categories: FakeCategoryRepository,
    transactions: TransactionRepository,
    clock: Clock,
    pairs: TransferPairRepository = FakeTransferPairRepository(FakeTransactionRepository()),
): CashEntryViewModel =
    CashEntryFakes(accounts, categories, transactions, pairs, FakeFailedInboxRepository())
        .viewModel(transactionId, null, clock)

/** A [CashEntryViewModel] in from-log mode: it books inbox item [inboxItemId] of [inbox]. */
internal fun fromLogViewModel(
    inboxItemId: String,
    inbox: FailedInboxRepository,
    accounts: FakeAccountRepository,
    categories: FakeCategoryRepository,
    transactions: TransactionRepository,
    clock: Clock,
): CashEntryViewModel =
    CashEntryFakes(accounts, categories, transactions, FakeTransferPairRepository(FakeTransactionRepository()), inbox)
        .viewModel(null, inboxItemId, clock)

private class CashEntryFakes(
    private val accounts: FakeAccountRepository,
    private val categories: FakeCategoryRepository,
    private val transactions: TransactionRepository,
    private val pairs: TransferPairRepository,
    private val inbox: FailedInboxRepository,
) {
    fun viewModel(
        transactionId: String?,
        inboxItemId: String?,
        clock: Clock,
    ): CashEntryViewModel {
        val runner = FakeTransactionRunner()
        val add = AddTransactionUseCase(transactions, runner, clock)
        return CashEntryViewModel(
            transactionId,
            inboxItemId,
            ObserveUsableAccountsUseCase(ObserveAccountsUseCase(accounts)),
            ObserveCategoriesUseCase(categories),
            GetLastManualChoicesUseCase(transactions),
            AddManualTransactionUseCase(add, clock),
            GetTransactionUseCase(transactions),
            UpdateTransactionUseCase(transactions, runner, clock),
            DeleteTransactionUseCase(transactions, runner, clock),
            UnpairTransferUseCase(transactions, pairs, runner, clock),
            IsAutoPairedUseCase(pairs),
            GetInboxItemUseCase(inbox),
            GetInboxPrefillUseCase(inbox, accounts),
            BookInboxItemUseCase(inbox, runner, clock),
            DismissInboxItemUseCase(inbox),
            clock,
            isDebuggable = true,
        )
    }
}
