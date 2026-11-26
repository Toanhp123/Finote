package io.github.toanhp123.finote.domain.report.usecase

import app.cash.turbine.test
import io.github.toanhp123.finote.domain.account.model.Account
import io.github.toanhp123.finote.domain.account.testAccount
import io.github.toanhp123.finote.domain.account.tx
import io.github.toanhp123.finote.domain.account.usecase.FakeAccountRepository
import io.github.toanhp123.finote.domain.category.usecase.FakeCategoryRepository
import io.github.toanhp123.finote.domain.transaction.model.Transaction
import io.github.toanhp123.finote.domain.transaction.model.TransactionType
import io.github.toanhp123.finote.domain.transaction.usecase.FakeTransactionRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

class ObserveMonthReportUseCaseTest {
    private val vn = ZoneId.of("Asia/Ho_Chi_Minh")
    private val clock = Clock.fixed(Instant.parse("2026-11-15T05:00:00Z"), vn)
    private val transactions = FakeTransactionRepository()
    private val accounts = FakeAccountRepository()
    private val categories = FakeCategoryRepository()
    private val input = ObserveReportInputUseCase(transactions, accounts, categories, clock)
    private val useCase = ObserveMonthReportUseCase(input, clock)

    private fun expense(
        id: String,
        amount: Long,
        at: String,
        account: String = "acc-1",
    ): Transaction =
        tx(TransactionType.EXPENSE, amount, account = account, at = Instant.parse(at).toEpochMilli()).copy(id = id)

    private fun account(
        id: String,
        ignored: Boolean = false,
    ): Account = testAccount(id = id).copy(isIgnored = ignored)

    @Test
    fun `loads last month and this month`() =
        runTest {
            transactions.transactionsFlow.value =
                listOf(
                    expense("oct", 40_000, "2026-10-03T05:00:00Z"),
                    expense("nov", 10_000, "2026-11-03T05:00:00Z"),
                    expense("sep", 99_000, "2026-09-30T05:00:00Z"),
                )
            val report = useCase(YearMonth.of(2026, 11)).first()
            assertEquals(10_000L, report.expense)
            assertEquals(40_000L, report.pace.lastMonth.last())
        }

    @Test
    fun `ignored account ids come from accounts`() =
        runTest {
            accounts.stored["acc-1"] = account("acc-1")
            accounts.stored["acc-2"] = account("acc-2", ignored = true)
            val reportInput = input(YearMonth.of(2026, 10), YearMonth.of(2026, 11)).first()
            assertEquals(setOf("acc-2"), reportInput.ignoredAccountIds)
            assertEquals(vn, reportInput.zone)
        }

    @Test
    fun `range is the start of the first month to the start of the month after the last`() =
        runTest {
            val start = Instant.parse("2026-09-30T17:00:00Z").toEpochMilli()
            val end = Instant.parse("2026-11-30T17:00:00Z").toEpochMilli()
            transactions.transactionsFlow.value =
                listOf(
                    expense("before", 1, "2026-09-30T16:59:59Z"),
                    expense("first", 2, "2026-09-30T17:00:00Z"),
                    expense("last", 3, "2026-11-30T16:59:59Z"),
                    expense("after", 4, "2026-11-30T17:00:00Z"),
                )
            val rows = input(YearMonth.of(2026, 10), YearMonth.of(2026, 11)).first().transactions
            assertEquals(setOf("first", "last"), rows.map { it.id }.toSet())
            assertTrue(rows.all { it.timestamp in start until end })
        }

    @Test
    fun `a new row re-emits`() =
        runTest {
            transactions.transactionsFlow.value = listOf(expense("a", 10_000, "2026-11-03T05:00:00Z"))
            useCase(YearMonth.of(2026, 11)).test {
                assertEquals(10_000L, awaitItem().expense)
                transactions.transactionsFlow.value += expense("b", 5_000, "2026-11-04T05:00:00Z")
                assertEquals(15_000L, awaitItem().expense)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `has transactions follows live rows`() =
        runTest {
            val useCase = ObserveHasTransactionsUseCase(transactions)
            useCase().test {
                assertEquals(false, awaitItem())
                transactions.transactionsFlow.value = listOf(expense("a", 1, "2026-11-03T05:00:00Z"))
                assertEquals(true, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }
}
