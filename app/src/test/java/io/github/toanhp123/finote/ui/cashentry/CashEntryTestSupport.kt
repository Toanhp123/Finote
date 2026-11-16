package io.github.toanhp123.finote.ui.cashentry

import io.github.toanhp123.finote.domain.account.usecase.FakeAccountRepository
import io.github.toanhp123.finote.domain.account.usecase.ObserveAccountsUseCase
import io.github.toanhp123.finote.domain.account.usecase.ObserveUsableAccountsUseCase
import io.github.toanhp123.finote.domain.category.usecase.FakeCategoryRepository
import io.github.toanhp123.finote.domain.category.usecase.ObserveCategoriesUseCase
import io.github.toanhp123.finote.domain.common.FakeTransactionRunner
import io.github.toanhp123.finote.domain.transaction.repository.TransactionRepository
import io.github.toanhp123.finote.domain.transaction.usecase.AddManualTransactionUseCase
import io.github.toanhp123.finote.domain.transaction.usecase.AddTransactionUseCase
import io.github.toanhp123.finote.domain.transaction.usecase.DeleteTransactionUseCase
import io.github.toanhp123.finote.domain.transaction.usecase.GetLastManualChoicesUseCase
import io.github.toanhp123.finote.domain.transaction.usecase.GetTransactionUseCase
import io.github.toanhp123.finote.domain.transaction.usecase.UpdateTransactionUseCase
import java.time.Clock

/** A [CashEntryViewModel] over fakes, as Hilt would build it. [transactionId] null adds, otherwise edits that row. */
internal fun cashEntryViewModel(
    transactionId: String?,
    accounts: FakeAccountRepository,
    categories: FakeCategoryRepository,
    transactions: TransactionRepository,
    clock: Clock,
): CashEntryViewModel {
    val runner = FakeTransactionRunner()
    val add = AddTransactionUseCase(transactions, runner, clock)
    return CashEntryViewModel(
        transactionId,
        ObserveUsableAccountsUseCase(ObserveAccountsUseCase(accounts)),
        ObserveCategoriesUseCase(categories),
        GetLastManualChoicesUseCase(transactions),
        AddManualTransactionUseCase(add, clock),
        GetTransactionUseCase(transactions),
        UpdateTransactionUseCase(transactions, runner, clock),
        DeleteTransactionUseCase(transactions, runner, clock),
        clock,
        isDebuggable = true,
    )
}
