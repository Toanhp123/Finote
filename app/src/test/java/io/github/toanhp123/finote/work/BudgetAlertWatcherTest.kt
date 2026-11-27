@file:OptIn(ExperimentalCoroutinesApi::class)

package io.github.toanhp123.finote.work

import io.github.toanhp123.finote.domain.account.tx
import io.github.toanhp123.finote.domain.account.usecase.FakeAccountRepository
import io.github.toanhp123.finote.domain.budget.FakeBudgetNotifier
import io.github.toanhp123.finote.domain.budget.FakeBudgetRepository
import io.github.toanhp123.finote.domain.budget.repository.BudgetRepository
import io.github.toanhp123.finote.domain.budget.testBudget
import io.github.toanhp123.finote.domain.budget.usecase.CheckBudgetAlertsUseCase
import io.github.toanhp123.finote.domain.budget.usecase.EnsureBudgetMonthUseCase
import io.github.toanhp123.finote.domain.budget.usecase.ObserveBudgetMonthUseCase
import io.github.toanhp123.finote.domain.budget.usecase.ObserveDataChangesUseCase
import io.github.toanhp123.finote.domain.category.testCategory
import io.github.toanhp123.finote.domain.category.usecase.FakeCategoryRepository
import io.github.toanhp123.finote.domain.common.FakeTransactionRunner
import io.github.toanhp123.finote.domain.common.StorageException
import io.github.toanhp123.finote.domain.report.usecase.ObserveReportInputUseCase
import io.github.toanhp123.finote.domain.transaction.model.TransactionType
import io.github.toanhp123.finote.domain.transaction.usecase.FakeTransactionRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneOffset
import javax.inject.Provider

class BudgetAlertWatcherTest {
    private val nov = YearMonth.of(2026, 11)
    private val clock = Clock.fixed(Instant.parse("2026-11-15T05:00:00Z"), ZoneOffset.UTC)
    private val budgets = FakeBudgetRepository()
    private val categories = FakeCategoryRepository()
    private val transactions = FakeTransactionRepository()
    private val notifier = FakeBudgetNotifier()
    private val runner = FakeTransactionRunner()
    private val changes = MutableSharedFlow<Unit>(extraBufferCapacity = 8)

    private var hasAnyCalls = 0
    private var changesResolved = 0

    // advanceUntilIdle skips background work, so these tests step virtual time with advanceTimeBy.

    /** Each check reads `hasAny` once and spends 100 ms there, like a slow query. [failures] are thrown first. */
    private fun watcher(
        scope: TestScope,
        failures: MutableList<Throwable> = mutableListOf(),
    ): BudgetAlertWatcher {
        val slow =
            object : BudgetRepository by budgets {
                override suspend fun hasAny(): Boolean {
                    hasAnyCalls++
                    delay(100)
                    failures.removeFirstOrNull()?.let { throw it }
                    return budgets.hasAny()
                }

                override fun observeChanges(): Flow<Unit> = changes
            }
        val ensure = EnsureBudgetMonthUseCase(slow, runner, clock)
        val input = ObserveReportInputUseCase(transactions, FakeAccountRepository(), categories, clock)
        val check =
            CheckBudgetAlertsUseCase(
                slow,
                categories,
                ObserveBudgetMonthUseCase(slow, ensure, input, clock),
                notifier,
                runner,
                clock,
            )
        return BudgetAlertWatcher(
            Provider {
                changesResolved++
                ObserveDataChangesUseCase(slow)
            },
            Provider { check },
            scope.backgroundScope,
        )
    }

    private fun givenOverThreshold() {
        categories.stored["food"] = testCategory(id = "food")
        budgets.stored["b"] = testBudget("b", nov, "food", limit = 1_000_000)
        budgets.setUp += nov
        transactions.transactionsFlow.value =
            listOf(
                tx(TransactionType.EXPENSE, 900_000, at = Instant.parse("2026-11-10T05:00:00Z").toEpochMilli())
                    .copy(id = "t1", categoryId = "food"),
            )
    }

    @Test
    fun `a burst of three changes runs the check at most twice and notifies once`() =
        runTest {
            givenOverThreshold()
            watcher(this).start()
            runCurrent()

            changes.tryEmit(Unit)
            runCurrent()
            changes.tryEmit(Unit)
            changes.tryEmit(Unit)
            advanceTimeBy(1_000)

            assertEquals(2, hasAnyCalls)
            assertEquals(listOf(80), notifier.alerts.map { it.mark })
        }

    @Test
    fun `start twice collects once`() =
        runTest {
            val watcher = watcher(this)

            watcher.start()
            watcher.start()
            runCurrent()

            assertEquals(1, changesResolved)
        }

    @Test
    fun `a storage error in one check does not stop the next`() =
        runTest {
            givenOverThreshold()
            watcher(this, mutableListOf(StorageException(IllegalStateException()))).start()
            runCurrent()

            changes.tryEmit(Unit)
            advanceTimeBy(1_000)
            changes.tryEmit(Unit)
            advanceTimeBy(1_000)

            assertEquals(2, hasAnyCalls)
            assertEquals(listOf(80), notifier.alerts.map { it.mark })
        }
}
