package io.github.toanhp123.finote.domain.account.usecase

import io.github.toanhp123.finote.domain.account.A
import io.github.toanhp123.finote.domain.account.B
import io.github.toanhp123.finote.domain.account.model.BalanceDrift
import io.github.toanhp123.finote.domain.account.testAccount
import io.github.toanhp123.finote.domain.account.tx
import io.github.toanhp123.finote.domain.transaction.model.Transaction
import io.github.toanhp123.finote.domain.transaction.model.TransactionType
import io.github.toanhp123.finote.domain.transaction.usecase.FakeTransactionRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ObserveBalanceDriftUseCaseTest {
    private val accounts = FakeAccountRepository()
    private val transactions = FakeTransactionRepository(activeAccounts = setOf(A, B))
    private val observeDrift =
        ObserveBalanceDriftUseCase(ObserveUsableAccountsUseCase(ObserveAccountsUseCase(accounts)), transactions)

    private fun income(
        id: String,
        account: String,
        amount: Long,
        at: Long,
        postBalance: Long? = null,
    ): Transaction =
        tx(TransactionType.INCOME, amount, account = account, at = at).copy(id = id, postBalance = postBalance)

    @Test
    fun `one account that overflows does not hide the drift of another`() =
        runTest {
            accounts.stored[A] = testAccount(A, initialBalance = Long.MAX_VALUE).copy(sortOrder = 0)
            accounts.stored[B] = testAccount(B, initialBalance = 1_000_000L).copy(sortOrder = 1)
            transactions.transactionsFlow.value =
                listOf(
                    income("a1", A, 1, at = 1),
                    income("a2", A, 1, at = 2, postBalance = 5),
                    income("b1", B, 500_000, at = 1, postBalance = 1_510_000),
                )

            assertEquals(listOf(BalanceDrift(B, 10_000)), observeDrift().first())
        }
}
