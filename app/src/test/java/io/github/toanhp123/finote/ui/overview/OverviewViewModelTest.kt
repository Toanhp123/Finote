package io.github.toanhp123.finote.ui.overview

import androidx.lifecycle.SavedStateHandle
import androidx.sqlite.SQLiteException
import app.cash.turbine.test
import io.github.toanhp123.finote.domain.account.tx
import io.github.toanhp123.finote.domain.account.usecase.FakeAccountRepository
import io.github.toanhp123.finote.domain.category.testCategory
import io.github.toanhp123.finote.domain.category.usecase.FakeCategoryRepository
import io.github.toanhp123.finote.domain.category.usecase.ObserveCategoriesUseCase
import io.github.toanhp123.finote.domain.common.StorageException
import io.github.toanhp123.finote.domain.report.model.ReportFilter
import io.github.toanhp123.finote.domain.report.usecase.ObserveHasTransactionsUseCase
import io.github.toanhp123.finote.domain.report.usecase.ObserveMonthReportUseCase
import io.github.toanhp123.finote.domain.report.usecase.ObserveReportInputUseCase
import io.github.toanhp123.finote.domain.transaction.model.Transaction
import io.github.toanhp123.finote.domain.transaction.model.TransactionType
import io.github.toanhp123.finote.domain.transaction.usecase.FakeTransactionRepository
import io.github.toanhp123.finote.ui.common.MainDispatcherRule
import io.github.toanhp123.finote.ui.common.component.display.ChartLine
import io.github.toanhp123.finote.ui.common.component.display.ChartSeries
import io.github.toanhp123.finote.ui.report.ReportListState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class OverviewViewModelTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    private val november = YearMonth.of(2026, 11)
    private val clock = MutableClock(Instant.parse("2026-11-15T05:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"))
    private val transactions = FakeTransactionRepository()
    private val accounts = FakeAccountRepository()
    private val categories = FakeCategoryRepository()

    private fun viewModel(
        handle: SavedStateHandle = SavedStateHandle(),
        listedCategories: FakeCategoryRepository = categories,
    ) = OverviewViewModel(
        savedStateHandle = handle,
        observeMonthReport =
            ObserveMonthReportUseCase(
                ObserveReportInputUseCase(transactions, accounts, categories, clock),
                clock,
            ),
        observeHasTransactions = ObserveHasTransactionsUseCase(transactions),
        observeCategories = ObserveCategoriesUseCase(listedCategories),
        clock = clock,
        isDebuggable = true,
    )

    private fun TestScope.open(
        handle: SavedStateHandle = SavedStateHandle(),
        listedCategories: FakeCategoryRepository = categories,
    ) = viewModel(handle, listedCategories).also { advanceUntilIdle() }

    private fun expense(
        id: String,
        amount: Long,
        at: String,
        category: String? = null,
    ): Transaction =
        tx(TransactionType.EXPENSE, amount, at = Instant.parse(at).toEpochMilli())
            .copy(id = id, categoryId = category)

    @Test
    fun `starts at the current month and shows its report`() =
        runTest(mainRule.dispatcher) {
            categories.stored["cat-1"] = testCategory("cat-1")
            transactions.transactionsFlow.value = listOf(expense("a", 10_000, "2026-11-03T05:00:00Z", "cat-1"))

            val state = open().state.value

            assertEquals(november, state.month)
            assertEquals(november, state.currentMonth)
            assertFalse(state.isLoading)
            assertFalse(state.hasError)
            assertTrue(state.hasTransactions)
            assertEquals(10_000L, state.report?.expense)
            assertEquals(setOf("cat-1"), state.categoriesById.keys)
        }

    @Test
    fun `no rows at all clears hasTransactions`() =
        runTest(mainRule.dispatcher) {
            val state = open().state.value

            assertFalse(state.hasTransactions)
            assertNotNull(state.report)
        }

    @Test
    fun `previous then next month moves and next stops at the current month`() =
        runTest(mainRule.dispatcher) {
            val vm = open()

            vm.onIntent(OverviewIntent.PreviousMonth)
            // The header moves at once and the body waits for the new month.
            assertEquals(YearMonth.of(2026, 10), vm.state.value.month)
            assertTrue(vm.state.value.isLoading)
            advanceUntilIdle()
            assertFalse(vm.state.value.isLoading)
            assertEquals(YearMonth.of(2026, 10), vm.state.value.month)
            assertEquals(
                YearMonth.of(2026, 10),
                vm.state.value.report
                    ?.month,
            )

            vm.onIntent(OverviewIntent.NextMonth)
            advanceUntilIdle()
            assertEquals(november, vm.state.value.month)

            vm.onIntent(OverviewIntent.NextMonth)
            advanceUntilIdle()
            assertEquals(november, vm.state.value.month)
        }

    @Test
    fun `month survives process death`() =
        runTest(mainRule.dispatcher) {
            val handle = SavedStateHandle()
            open(handle).onIntent(OverviewIntent.PreviousMonth)
            advanceUntilIdle()

            val revived = viewModel(handle)

            assertEquals(YearMonth.of(2026, 10), revived.state.value.month)
            advanceUntilIdle()
            assertEquals(
                YearMonth.of(2026, 10),
                revived.state.value.report
                    ?.month,
            )
        }

    @Test
    fun `a new row re-emits the report`() =
        runTest(mainRule.dispatcher) {
            transactions.transactionsFlow.value = listOf(expense("a", 10_000, "2026-11-03T05:00:00Z"))
            val vm = open()

            vm.state.test {
                assertEquals(10_000L, awaitItem().report?.expense)
                transactions.transactionsFlow.value += expense("b", 5_000, "2026-11-04T05:00:00Z")
                assertEquals(15_000L, awaitItem().report?.expense)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `bucket click sends OpenReport with month and filter`() =
        runTest(mainRule.dispatcher) {
            val vm = open()
            vm.onIntent(OverviewIntent.PreviousMonth)
            advanceUntilIdle()

            vm.effects.test {
                vm.onIntent(OverviewIntent.BucketClicked(ReportFilter.TransferFees))
                assertEquals(
                    OverviewEffect.OpenReport(YearMonth.of(2026, 10), ReportFilter.TransferFees),
                    awaitItem(),
                )
            }
        }

    @Test
    fun `merchant click sends the ByMerchant filter`() =
        runTest(mainRule.dispatcher) {
            val vm = open()

            vm.effects.test {
                vm.onIntent(OverviewIntent.MerchantClicked("Highlands"))
                assertEquals(
                    OverviewEffect.OpenReport(november, ReportFilter.ByMerchant("Highlands")),
                    awaitItem(),
                )
            }
        }

    @Test
    fun `toggle buckets expands and collapses`() =
        runTest(mainRule.dispatcher) {
            val vm = open()

            vm.onIntent(OverviewIntent.ToggleBuckets)
            assertTrue(vm.state.value.isBucketListExpanded)
            vm.onIntent(OverviewIntent.ToggleBuckets)
            assertFalse(vm.state.value.isBucketListExpanded)
        }

    @Test
    fun `storage error sets hasError and another month retries`() =
        runTest(mainRule.dispatcher) {
            transactions.failObserve = StorageException(SQLiteException("disk"))
            val vm = open()
            assertTrue(vm.state.value.hasError)
            assertFalse(vm.state.value.isLoading)
            assertNull(vm.state.value.report)

            transactions.failObserve = null
            vm.onIntent(OverviewIntent.PreviousMonth)
            advanceUntilIdle()

            assertFalse(vm.state.value.hasError)
            assertEquals(
                YearMonth.of(2026, 10),
                vm.state.value.report
                    ?.month,
            )
        }

    @Test
    fun `a storage error after a good report clears the stale report`() =
        runTest(mainRule.dispatcher) {
            // The category list is a side flow of the combine, apart from the one the report reads.
            val listed = FakeCategoryRepository()
            val vm = open(listedCategories = listed)
            assertNotNull(vm.state.value.report)

            listed.failObserve = StorageException(SQLiteException("disk"))
            listed.notifyChanged()
            advanceUntilIdle()

            assertTrue(vm.state.value.hasError)
            assertNull(vm.state.value.report)
        }

    @Test
    fun `resume on a new day moves the pace without leaving the shown month`() =
        runTest(mainRule.dispatcher) {
            val vm = open()
            assertEquals(
                15,
                vm.state.value.report
                    ?.pace
                    ?.thisMonth
                    ?.size,
            )

            clock.now = Instant.parse("2026-11-16T05:00:00Z")
            vm.onIntent(OverviewIntent.Resumed)
            advanceUntilIdle()

            assertEquals(november, vm.state.value.month)
            assertEquals(
                16,
                vm.state.value.report
                    ?.pace
                    ?.thisMonth
                    ?.size,
            )
        }

    @Test
    fun `resume after the month ends shows the old month as a past month`() =
        runTest(mainRule.dispatcher) {
            val vm = open()
            clock.now = Instant.parse("2026-11-30T17:30:00Z") // 00:30 on 1 December in Ho Chi Minh City

            vm.onIntent(OverviewIntent.Resumed)
            advanceUntilIdle()

            assertEquals(november, vm.state.value.month)
            assertEquals(YearMonth.of(2026, 12), vm.state.value.currentMonth)
            assertEquals(
                30,
                vm.state.value.report
                    ?.pace
                    ?.thisMonth
                    ?.size,
            )
        }

    @Test
    fun `state, intent and chart strings carry no amount or merchant name`() {
        val merchant = "Highlands"
        val series = ChartSeries(listOf(123_456_789L, 987_654_321L), ChartLine.Primary)
        val listState =
            ReportListState(
                month = november,
                filter = ReportFilter.ByMerchant(merchant),
                total = 135_792_468L,
            )
        val text =
            listOf(
                OverviewIntent.MerchantClicked(merchant),
                OverviewEffect.OpenReport(november, ReportFilter.ByMerchant(merchant)),
                OverviewState(month = november, currentMonth = november),
                series,
                listState,
            ).joinToString { it.toString() }

        assertFalse(text.contains(merchant))
        assertFalse(text.contains("123456789"))
        assertFalse(text.contains("135792468"))
    }

    @Test
    fun `new month after midnight moves currentMonth on resume and on a new emission`() =
        runTest(mainRule.dispatcher) {
            val vm = open()
            clock.now = Instant.parse("2026-11-30T17:30:00Z") // 00:30 on 1 December in Ho Chi Minh City

            assertEquals(november, vm.state.value.currentMonth)
            vm.onIntent(OverviewIntent.Resumed)
            assertEquals(YearMonth.of(2026, 12), vm.state.value.currentMonth)

            clock.now = Instant.parse("2026-12-31T17:30:00Z")
            transactions.transactionsFlow.value = listOf(expense("a", 1, "2026-12-03T05:00:00Z"))
            advanceUntilIdle()
            assertEquals(YearMonth.of(2027, 1), vm.state.value.currentMonth)
        }
}

private class MutableClock(
    var now: Instant,
    private val zone: ZoneId,
) : Clock() {
    override fun getZone(): ZoneId = zone

    override fun withZone(zone: ZoneId): Clock = MutableClock(now, zone)

    override fun instant(): Instant = now
}
