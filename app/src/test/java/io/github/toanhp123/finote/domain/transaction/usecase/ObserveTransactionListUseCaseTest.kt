package io.github.toanhp123.finote.domain.transaction.usecase

import app.cash.turbine.test
import io.github.toanhp123.finote.domain.account.A
import io.github.toanhp123.finote.domain.account.B
import io.github.toanhp123.finote.domain.account.testAccount
import io.github.toanhp123.finote.domain.account.tx
import io.github.toanhp123.finote.domain.account.usecase.FakeAccountRepository
import io.github.toanhp123.finote.domain.transaction.model.ReimbursementStatus
import io.github.toanhp123.finote.domain.transaction.model.TransactionType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class ObserveTransactionListUseCaseTest {
    private val repository = FakeTransactionRepository()
    private val accounts = FakeAccountRepository()
    private val clock = Clock.fixed(Instant.parse("2026-11-04T03:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"))
    private val useCase = ObserveTransactionListUseCase(repository, accounts, clock)

    @Test
    fun `passes the account and groups in the clock zone`() =
        runTest {
            val at = Instant.parse("2026-11-04T17:30:00Z").toEpochMilli()
            repository.transactionsFlow.value = listOf(tx(TransactionType.EXPENSE, 10_000, at = at))

            val days = useCase("acc-a", pendingOnly = false).first()

            assertEquals(listOf(LocalDate.of(2026, 11, 5)), days.map { it.date })
            assertEquals(listOf<String?>("acc-a"), repository.observedAccountIds)
        }

    @Test
    fun `pending only keeps the pending rows and passes the account through`() =
        runTest {
            val pending =
                tx(
                    TransactionType.EXPENSE,
                    10_000,
                ).copy(id = "p", reimbursementStatus = ReimbursementStatus.PENDING)
            val settled = pending.copy(id = "s", reimbursementStatus = ReimbursementStatus.SETTLED)
            val plain = pending.copy(id = "n", reimbursementStatus = ReimbursementStatus.NONE)
            repository.transactionsFlow.value = listOf(pending, settled, plain)

            val days = useCase(null, pendingOnly = true).first()

            assertEquals(listOf("p"), days.flatMap { it.transactions }.map { it.id })
            assertEquals(listOf<String?>(null), repository.observedAccountIds)
        }

    @Test
    fun `without pending only every row stays`() =
        runTest {
            val pending =
                tx(
                    TransactionType.EXPENSE,
                    10_000,
                ).copy(id = "p", reimbursementStatus = ReimbursementStatus.PENDING)
            repository.transactionsFlow.value =
                listOf(pending, pending.copy(id = "n", reimbursementStatus = ReimbursementStatus.NONE))

            val days = useCase(null, pendingOnly = false).first()

            assertEquals(2, days.sumOf { it.transactions.size })
        }

    private fun ignoreA() {
        accounts.stored[A] = testAccount(A).copy(isIgnored = true)
        accounts.stored[B] = testAccount(B)
    }

    @Test
    fun `all accounts hides rows of an ignored account`() =
        runTest {
            ignoreA()
            repository.transactionsFlow.value =
                listOf(
                    tx(TransactionType.EXPENSE, 10_000, account = A).copy(id = "a"),
                    tx(TransactionType.EXPENSE, 20_000, account = B).copy(id = "b"),
                )

            val days = useCase(null, pendingOnly = false).first()

            assertEquals(listOf("b"), days.flatMap { it.transactions }.map { it.id })
            assertEquals(listOf(-20_000L), days.map { it.net })
        }

    @Test
    fun `a chosen account still shows a transfer from an ignored account`() =
        runTest {
            ignoreA()
            repository.transactionsFlow.value =
                listOf(tx(TransactionType.TRANSFER, 10_000, account = A, to = B).copy(id = "t"))

            val days = useCase(B, pendingOnly = false).first()

            assertEquals(listOf("t"), days.flatMap { it.transactions }.map { it.id })
        }

    @Test
    fun `pending chip keeps a pending row of an ignored account`() =
        runTest {
            ignoreA()
            repository.transactionsFlow.value =
                listOf(
                    tx(TransactionType.EXPENSE, 10_000, account = A)
                        .copy(id = "p", reimbursementStatus = ReimbursementStatus.PENDING),
                )

            val days = useCase(null, pendingOnly = true).first()

            assertEquals(listOf("p"), days.flatMap { it.transactions }.map { it.id })
        }

    @Test
    fun `un-ignoring an account brings its rows back`() =
        runTest {
            ignoreA()
            repository.transactionsFlow.value = listOf(tx(TransactionType.EXPENSE, 10_000, account = A))
            useCase(null, pendingOnly = false).test {
                assertEquals(0, awaitItem().size)
                accounts.stored[A] = testAccount(A)
                accounts.notifyChanged()
                assertEquals(1, awaitItem().size)
                cancelAndIgnoreRemainingEvents()
            }
        }
}
