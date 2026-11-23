package io.github.toanhp123.finote.domain.account.usecase

import app.cash.turbine.test
import io.github.toanhp123.finote.domain.account.A
import io.github.toanhp123.finote.domain.account.B
import io.github.toanhp123.finote.domain.account.model.BalanceDrift
import io.github.toanhp123.finote.domain.account.model.DriftKey
import io.github.toanhp123.finote.domain.account.service.balanceDriftKey
import io.github.toanhp123.finote.domain.account.testAccount
import io.github.toanhp123.finote.domain.account.tx
import io.github.toanhp123.finote.domain.transaction.model.Transaction
import io.github.toanhp123.finote.domain.transaction.model.TransactionType
import io.github.toanhp123.finote.domain.transaction.usecase.FakeTransactionRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ObserveBalanceDriftUseCaseTest {
    private val accounts = FakeAccountRepository()
    private val transactions = FakeTransactionRepository(activeAccounts = setOf(A, B))
    private val acks = FakeDriftAcknowledgementRepository()
    private val observeDrift =
        ObserveBalanceDriftUseCase(ObserveUsableAccountsUseCase(ObserveAccountsUseCase(accounts)), transactions, acks)

    private fun income(
        id: String,
        account: String,
        amount: Long,
        at: Long,
        postBalance: Long? = null,
    ): Transaction =
        tx(TransactionType.INCOME, amount, account = account, at = at).copy(id = id, postBalance = postBalance)

    /** One account whose bank balance (1_510_000) is 10_000 above the app's (1_500_000). */
    private fun driftingRow(amount: Long = 500_000) = income("a1", A, amount, at = 1, postBalance = 1_510_000)

    private fun seedDrifting(row: Transaction = driftingRow()) {
        accounts.stored[A] = testAccount(A, initialBalance = 1_000_000L)
        transactions.transactionsFlow.value = listOf(row)
    }

    private fun keyOf(row: Transaction): DriftKey =
        checkNotNull(balanceDriftKey(testAccount(A, initialBalance = 1_000_000L), listOf(row)))

    @Test
    fun `drift shows when key is not acknowledged`() =
        runTest {
            seedDrifting()

            observeDrift().test {
                assertEquals(listOf(BalanceDrift(A)), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `acknowledged drift is hidden`() =
        runTest {
            seedDrifting()
            acks.acknowledge(A, keyOf(driftingRow()))

            observeDrift().test {
                assertEquals(emptyList<BalanceDrift>(), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `acknowledged drift returns when the ledger changes`() =
        runTest {
            seedDrifting()
            acks.acknowledge(A, keyOf(driftingRow()))

            observeDrift().test {
                assertEquals(emptyList<BalanceDrift>(), awaitItem())
                // Editing the amount moves the ledger balance, so the stored key no longer matches.
                transactions.transactionsFlow.value = listOf(driftingRow(amount = 600_000))
                assertEquals(listOf(BalanceDrift(A)), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `a stored value in an unknown format does not hide the drift`() =
        runTest {
            seedDrifting()
            acks.acknowledged.value = mapOf(A to "5:1500000")

            observeDrift().test {
                assertEquals(listOf(BalanceDrift(A)), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `overflow in one account keeps the others`() =
        runTest {
            accounts.stored[A] = testAccount(A, initialBalance = Long.MAX_VALUE).copy(sortOrder = 0)
            accounts.stored[B] = testAccount(B, initialBalance = 1_000_000L).copy(sortOrder = 1)
            transactions.transactionsFlow.value =
                listOf(
                    income("a1", A, 1, at = 1),
                    income("a2", A, 1, at = 2, postBalance = 5),
                    income("b1", B, 500_000, at = 1, postBalance = 1_510_000),
                )

            observeDrift().test {
                assertEquals(listOf(BalanceDrift(B)), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }
}
