package io.github.toanhp123.finote.domain.report.usecase

import io.github.toanhp123.finote.domain.account.adjustment
import io.github.toanhp123.finote.domain.account.testAccount
import io.github.toanhp123.finote.domain.account.tx
import io.github.toanhp123.finote.domain.account.usecase.FakeAccountRepository
import io.github.toanhp123.finote.domain.category.usecase.FakeCategoryRepository
import io.github.toanhp123.finote.domain.transaction.model.AdjustmentDirection
import io.github.toanhp123.finote.domain.transaction.model.Transaction
import io.github.toanhp123.finote.domain.transaction.model.TransactionType
import io.github.toanhp123.finote.domain.transaction.usecase.FakeTransactionRepository
import io.github.toanhp123.finote.domain.transaction.usecase.ObserveTransactionListUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

/** The ledger's day nets and the Overview net must be the same number for the same month. */
class LedgerReportMatchTest {
    private val clock = Clock.fixed(Instant.parse("2026-11-15T05:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"))
    private val transactions = FakeTransactionRepository()
    private val accounts = FakeAccountRepository()
    private val month = YearMonth.of(2026, 11)

    private fun at(day: String) = Instant.parse("2026-$day" + "T05:00:00Z").toEpochMilli()

    private fun Transaction.row(
        id: String,
        day: String,
    ) = copy(id = id, timestamp = at(day))

    @Test
    fun `day nets of a month add up to the report net`() =
        runTest {
            accounts.stored["acc-a"] = testAccount("acc-a")
            accounts.stored["acc-b"] = testAccount("acc-b")
            accounts.stored["acc-i"] = testAccount("acc-i").copy(isIgnored = true)
            accounts.stored["acc-r"] = testAccount("acc-r").copy(isArchived = true)
            transactions.transactionsFlow.value =
                listOf(
                    tx(TransactionType.INCOME, 1_000_000).row("income", "11-03"),
                    tx(TransactionType.EXPENSE, 100_000).row("expense", "11-04"),
                    tx(TransactionType.REFUND, 30_000).row("refund", "11-05"),
                    tx(TransactionType.TRANSFER, 200_000, fee = 5_000, to = "acc-b").row("transfer", "11-06"),
                    adjustment(AdjustmentDirection.IN, 90_000).row("adjust", "11-07"),
                    tx(TransactionType.EXPENSE, 50_000, fee = 2_000)
                        .copy(isExcludedFromAnalytics = true)
                        .row("excluded", "11-08"),
                    tx(TransactionType.EXPENSE, 70_000, account = "acc-i").row("ignored", "11-09"),
                    tx(TransactionType.EXPENSE, 40_000, account = "acc-r").row("archived", "11-10"),
                    tx(TransactionType.EXPENSE, 60_000).row("october", "10-20"),
                )

            val days = ObserveTransactionListUseCase(transactions, accounts, clock)(null, false).first()
            val ledgerNet = days.filter { YearMonth.from(it.date) == month }.sumOf { it.net }
            val input = ObserveReportInputUseCase(transactions, accounts, FakeCategoryRepository(), clock)
            val report = ObserveMonthReportUseCase(input, clock)(month).first()

            assertEquals(885_000L, report.net)
            assertEquals(report.net, ledgerNet)
        }
}
