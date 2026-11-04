package io.github.toanhp123.finote.domain.transaction.usecase

import io.github.toanhp123.finote.domain.account.tx
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
    private val clock = Clock.fixed(Instant.parse("2026-11-04T03:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"))
    private val useCase = ObserveTransactionListUseCase(repository, clock)

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
}
