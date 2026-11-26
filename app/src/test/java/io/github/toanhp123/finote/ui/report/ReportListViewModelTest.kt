package io.github.toanhp123.finote.ui.report

import androidx.sqlite.SQLiteException
import app.cash.turbine.test
import io.github.toanhp123.finote.domain.account.A
import io.github.toanhp123.finote.domain.account.B
import io.github.toanhp123.finote.domain.account.testAccount
import io.github.toanhp123.finote.domain.account.tx
import io.github.toanhp123.finote.domain.account.usecase.FakeAccountRepository
import io.github.toanhp123.finote.domain.account.usecase.ObserveAccountsUseCase
import io.github.toanhp123.finote.domain.category.testCategory
import io.github.toanhp123.finote.domain.category.usecase.FakeCategoryRepository
import io.github.toanhp123.finote.domain.category.usecase.ObserveCategoriesUseCase
import io.github.toanhp123.finote.domain.common.StorageException
import io.github.toanhp123.finote.domain.report.model.ReportFilter
import io.github.toanhp123.finote.domain.report.usecase.ObserveReportInputUseCase
import io.github.toanhp123.finote.domain.report.usecase.ObserveReportRowsUseCase
import io.github.toanhp123.finote.domain.transaction.model.Transaction
import io.github.toanhp123.finote.domain.transaction.model.TransactionType
import io.github.toanhp123.finote.domain.transaction.usecase.FakeTransactionRepository
import io.github.toanhp123.finote.ui.common.MainDispatcherRule
import io.github.toanhp123.finote.ui.report.logic.toNavKey
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class ReportListViewModelTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    private val november = YearMonth.of(2026, 11)
    private val clock = Clock.fixed(Instant.parse("2026-11-15T05:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"))
    private val at = Instant.parse("2026-11-03T05:00:00Z").toEpochMilli()
    private val transactions = FakeTransactionRepository()
    private val accounts = FakeAccountRepository()
    private val categories = FakeCategoryRepository()

    init {
        accounts.stored[A] = testAccount(A)
        accounts.stored[B] = testAccount(B).copy(isArchived = true)
        categories.stored["cat-1"] = testCategory("cat-1")
        categories.stored["cat-2"] = testCategory("cat-2", name = "Rent")
    }

    private fun TestScope.open(filter: ReportFilter): ReportListViewModel {
        val input = ObserveReportInputUseCase(transactions, accounts, categories, clock)
        val vm =
            ReportListViewModel(
                key = filter.toNavKey(november),
                observeRows = ObserveReportRowsUseCase(input),
                observeAccounts = ObserveAccountsUseCase(accounts),
                observeCategories = ObserveCategoriesUseCase(categories),
                isDebuggable = true,
            )
        advanceUntilIdle()
        return vm
    }

    private fun row(
        id: String,
        amount: Long,
        category: String? = "cat-1",
        type: TransactionType = TransactionType.EXPENSE,
        account: String = A,
        fee: Long = 0,
    ): Transaction =
        tx(type, amount, fee = fee, account = account, to = if (type == TransactionType.TRANSFER) B else null, at = at)
            .copy(id = id, categoryId = category)

    @Test
    fun `shows rows and total of the filter`() =
        runTest(mainRule.dispatcher) {
            transactions.transactionsFlow.value =
                listOf(
                    row("a", 10_000),
                    row("refund", 2_000, type = TransactionType.REFUND),
                    row("other", 5_000, category = "cat-2"),
                )

            val state = open(ReportFilter.ByCategory("cat-1")).state.value

            assertFalse(state.isLoading)
            assertEquals(
                mapOf("a" to 10_000L, "refund" to -2_000L),
                state.rows.associate {
                    it.transaction.id to
                        it.amount
                },
            )
            assertEquals(8_000L, state.total)
            assertEquals(november, state.month)
            assertEquals(setOf("cat-1", "cat-2"), state.categoriesById.keys)
        }

    @Test
    fun `fee list shows the fee as the row amount`() =
        runTest(mainRule.dispatcher) {
            transactions.transactionsFlow.value =
                listOf(row("t", 500_000, type = TransactionType.TRANSFER, fee = 7_000))

            val state = open(ReportFilter.TransferFees).state.value

            assertEquals(listOf(7_000L), state.rows.map { it.amount })
            assertEquals(7_000L, state.total)
        }

    @Test
    fun `rows moved away leave an empty list with total zero`() =
        runTest(mainRule.dispatcher) {
            val a = row("a", 10_000)
            transactions.transactionsFlow.value = listOf(a)
            val vm = open(ReportFilter.ByCategory("cat-1"))
            assertEquals(10_000L, vm.state.value.total)

            transactions.transactionsFlow.value = listOf(a.copy(categoryId = "cat-2"))
            advanceUntilIdle()

            assertTrue(
                vm.state.value.rows
                    .isEmpty(),
            )
            assertEquals(0L, vm.state.value.total)
            assertFalse(vm.state.value.hasError)
        }

    @Test
    fun `storage error sets hasError`() =
        runTest(mainRule.dispatcher) {
            transactions.failObserve = StorageException(SQLiteException("disk"))

            val state = open(ReportFilter.Uncategorized).state.value

            assertTrue(state.hasError)
            assertFalse(state.isLoading)
        }

    @Test
    fun `row of an archived account does not open`() =
        runTest(mainRule.dispatcher) {
            transactions.transactionsFlow.value = listOf(row("archived", 10_000, account = B))
            val vm = open(ReportFilter.ByCategory("cat-1"))

            vm.effects.test {
                vm.onIntent(ReportListIntent.RowClicked("archived"))
                runCurrent()
                expectNoEvents()
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `openable row sends OpenEditor`() =
        runTest(mainRule.dispatcher) {
            transactions.transactionsFlow.value = listOf(row("live", 10_000))
            val vm = open(ReportFilter.ByCategory("cat-1"))

            vm.effects.test {
                vm.onIntent(ReportListIntent.RowClicked("live"))
                assertEquals(ReportListEffect.OpenEditor("live"), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }
}
