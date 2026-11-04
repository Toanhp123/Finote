package io.github.toanhp123.finote.ui.transactions

import androidx.lifecycle.SavedStateHandle
import androidx.sqlite.SQLiteException
import app.cash.turbine.test
import io.github.toanhp123.finote.domain.account.A
import io.github.toanhp123.finote.domain.account.B
import io.github.toanhp123.finote.domain.account.C
import io.github.toanhp123.finote.domain.account.testAccount
import io.github.toanhp123.finote.domain.account.tx
import io.github.toanhp123.finote.domain.account.usecase.FakeAccountRepository
import io.github.toanhp123.finote.domain.category.testCategory
import io.github.toanhp123.finote.domain.category.usecase.FakeCategoryRepository
import io.github.toanhp123.finote.domain.transaction.model.TransactionType
import io.github.toanhp123.finote.domain.transaction.usecase.FakeTransactionRepository
import io.github.toanhp123.finote.domain.transaction.usecase.ObserveTransactionListUseCase
import io.github.toanhp123.finote.ui.common.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class TransactionListViewModelTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    private val clock = MutableClock(Instant.parse("2026-11-04T03:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"))
    private val accounts = FakeAccountRepository()
    private val categories = FakeCategoryRepository()
    private val fakeTx = FakeTransactionRepository()

    private fun seed() {
        accounts.stored[A] = testAccount(A)
        accounts.stored[B] = testAccount(B).copy(isArchived = true)
        categories.stored["cat-1"] = testCategory("cat-1")
    }

    private fun viewModel(handle: SavedStateHandle = SavedStateHandle()) =
        TransactionListViewModel(
            savedStateHandle = handle,
            observeList = ObserveTransactionListUseCase(fakeTx, clock),
            accountRepository = accounts,
            categoryRepository = categories,
            clock = clock,
        )

    private fun TestScope.open(handle: SavedStateHandle = SavedStateHandle()) = viewModel(handle).also { runCurrent() }

    @Test
    fun `starts loading with today from the clock`() =
        runTest(mainRule.dispatcher) {
            val vm = viewModel()

            assertTrue(vm.state.value.isLoading)
            assertEquals(LocalDate.of(2026, 11, 4), vm.state.value.today)
        }

    @Test
    fun `shows days and only unarchived accounts as chips`() =
        runTest(mainRule.dispatcher) {
            seed()
            fakeTx.transactionsFlow.value = listOf(tx(TransactionType.EXPENSE, 10_000).copy(id = "t1"))
            val vm = viewModel()

            vm.state.test {
                assertTrue(awaitItem().isLoading)
                runCurrent()
                val loaded = awaitItem()
                assertFalse(loaded.isLoading)
                assertFalse(loaded.hasError)
                assertEquals(1, loaded.days.size)
                assertEquals(listOf(A), loaded.accounts.map { it.id })
                assertEquals(setOf(A, B), loaded.accountsById.keys)
                assertEquals(setOf("cat-1"), loaded.categoriesById.keys)
            }
        }

    @Test
    fun `select account asks for that account`() =
        runTest(mainRule.dispatcher) {
            seed()
            fakeTx.transactionsFlow.value =
                listOf(
                    tx(TransactionType.EXPENSE, 10_000).copy(id = "in-a"),
                    tx(TransactionType.EXPENSE, 20_000, account = B).copy(id = "in-b"),
                )
            val vm = open()
            assertEquals(
                listOf("in-a", "in-b"),
                vm.state.value.days
                    .single()
                    .transactions
                    .map { it.id },
            )

            vm.onIntent(TransactionListIntent.SelectAccount(A))
            runCurrent()

            assertEquals(A, vm.state.value.selectedAccountId)
            assertEquals(A, fakeTx.observedAccountIds.last())
            assertEquals(
                listOf("in-a"),
                vm.state.value.days
                    .single()
                    .transactions
                    .map { it.id },
            )
        }

    @Test
    fun `today follows the clock when a new list arrives`() =
        runTest(mainRule.dispatcher) {
            seed()
            val vm = open()
            assertEquals(LocalDate.of(2026, 11, 4), vm.state.value.today)

            clock.now = Instant.parse("2026-11-04T18:00:00Z")
            fakeTx.transactionsFlow.value = listOf(tx(TransactionType.EXPENSE, 10_000))
            runCurrent()

            assertEquals(LocalDate.of(2026, 11, 5), vm.state.value.today)
        }

    @Test
    fun `selection survives in SavedStateHandle`() =
        runTest(mainRule.dispatcher) {
            seed()

            val vm = open(SavedStateHandle(mapOf("selected_account_id" to A)))

            assertEquals(A, vm.state.value.selectedAccountId)
            assertFalse(vm.state.value.isLoading)
        }

    @Test
    fun `archiving the selected account resets to all`() =
        runTest(mainRule.dispatcher) {
            seed()
            val vm = open()
            vm.onIntent(TransactionListIntent.SelectAccount(A))
            runCurrent()

            accounts.stored[A] = checkNotNull(accounts.stored[A]).copy(isArchived = true)
            accounts.notifyChanged()
            runCurrent()

            assertNull(vm.state.value.selectedAccountId)
            assertNull(fakeTx.observedAccountIds.last())
            assertTrue(
                vm.state.value.accounts
                    .isEmpty(),
            )
        }

    @Test
    fun `flow error sets hasError`() =
        runTest(mainRule.dispatcher) {
            seed()
            fakeTx.failObserve = SQLiteException("disk")

            val state = open().state.value

            assertTrue(state.hasError)
            assertFalse(state.isLoading)
        }

    @Test
    fun `picking another chip retries after an error`() =
        runTest(mainRule.dispatcher) {
            seed()
            accounts.stored[C] = testAccount(C)
            fakeTx.failObserve = SQLiteException("disk")
            val vm = open()
            assertTrue(vm.state.value.hasError)

            fakeTx.failObserve = null
            fakeTx.transactionsFlow.value = listOf(tx(TransactionType.EXPENSE, 10_000, account = C))
            vm.onIntent(TransactionListIntent.SelectAccount(C))
            runCurrent()

            assertFalse(vm.state.value.hasError)
            assertEquals(1, vm.state.value.days.size)
            assertEquals(C, vm.state.value.selectedAccountId)
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
