package io.github.toanhp123.finote.domain.transaction.usecase

import io.github.toanhp123.finote.domain.account.tx
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

            val days = useCase("acc-a").first()

            assertEquals(listOf(LocalDate.of(2026, 11, 5)), days.map { it.date })
            assertEquals(listOf<String?>("acc-a"), repository.observedAccountIds)
        }
}
