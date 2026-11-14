package io.github.toanhp123.finote.ui.transactions

import androidx.lifecycle.SavedStateHandle
import androidx.sqlite.SQLiteException
import app.cash.turbine.test
import io.github.toanhp123.finote.domain.account.A
import io.github.toanhp123.finote.domain.account.B
import io.github.toanhp123.finote.domain.account.C
import io.github.toanhp123.finote.domain.account.model.BalanceDrift
import io.github.toanhp123.finote.domain.account.testAccount
import io.github.toanhp123.finote.domain.account.tx
import io.github.toanhp123.finote.domain.account.usecase.FakeAccountRepository
import io.github.toanhp123.finote.domain.account.usecase.ObserveAccountsUseCase
import io.github.toanhp123.finote.domain.account.usecase.ObserveBalanceDriftUseCase
import io.github.toanhp123.finote.domain.account.usecase.ObserveUsableAccountsUseCase
import io.github.toanhp123.finote.domain.category.testCategory
import io.github.toanhp123.finote.domain.category.usecase.FakeCategoryRepository
import io.github.toanhp123.finote.domain.category.usecase.ObserveCategoriesUseCase
import io.github.toanhp123.finote.domain.common.FakeTransactionRunner
import io.github.toanhp123.finote.domain.common.StorageException
import io.github.toanhp123.finote.domain.transaction.model.ReimbursementStatus
import io.github.toanhp123.finote.domain.transaction.model.Transaction
import io.github.toanhp123.finote.domain.transaction.model.TransactionType
import io.github.toanhp123.finote.domain.transaction.usecase.AdjustBalanceDriftUseCase
import io.github.toanhp123.finote.domain.transaction.usecase.FakeTransactionRepository
import io.github.toanhp123.finote.domain.transaction.usecase.ObserveTransactionListUseCase
import io.github.toanhp123.finote.domain.transaction.usecase.UpdateTransactionUseCase
import io.github.toanhp123.finote.ui.common.MainDispatcherRule
import io.github.toanhp123.finote.ui.transactions.component.ReimbursementChoice
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
    private val fakeTx = FakeTransactionRepository(activeAccounts = setOf(A))

    private fun seed() {
        accounts.stored[A] = testAccount(A)
        accounts.stored[B] = testAccount(B).copy(isArchived = true)
        categories.stored["cat-1"] = testCategory("cat-1")
    }

    private fun viewModel(handle: SavedStateHandle = SavedStateHandle()) =
        TransactionListViewModel(
            savedStateHandle = handle,
            observeList = ObserveTransactionListUseCase(fakeTx, clock),
            observeAccounts = ObserveAccountsUseCase(accounts),
            observeCategories = ObserveCategoriesUseCase(categories),
            updateTransaction = UpdateTransactionUseCase(fakeTx, FakeTransactionRunner(), clock),
            observeDrifts =
                ObserveBalanceDriftUseCase(ObserveUsableAccountsUseCase(ObserveAccountsUseCase(accounts)), fakeTx),
            adjustBalanceDrift = AdjustBalanceDriftUseCase(fakeTx, FakeTransactionRunner(), clock),
            clock = clock,
            isDebuggable = true,
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
                    tx(TransactionType.EXPENSE, 10_000, at = 2).copy(id = "in-a"),
                    tx(TransactionType.EXPENSE, 20_000, account = B, at = 1).copy(id = "in-b"),
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
            fakeTx.failObserve = StorageException(SQLiteException("disk"))

            val state = open().state.value

            assertTrue(state.hasError)
            assertFalse(state.isLoading)
        }

    @Test
    fun `picking another chip retries after an error`() =
        runTest(mainRule.dispatcher) {
            seed()
            accounts.stored[C] = testAccount(C)
            fakeTx.failObserve = StorageException(SQLiteException("disk"))
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

    private fun row(
        id: String,
        type: TransactionType = TransactionType.EXPENSE,
        account: String = A,
        status: ReimbursementStatus = ReimbursementStatus.NONE,
    ): Transaction =
        tx(type, 10_000, account = account, at = ROW_AT)
            .copy(id = id, reimbursementStatus = status, isExcludedFromAnalytics = status != ReimbursementStatus.NONE)

    private fun store(vararg rows: Transaction) {
        rows.forEach { fakeTx.stored[it.id] = it }
        fakeTx.transactionsFlow.value = rows.toList()
    }

    @Test
    fun `expense row click opens the sheet and an income row click does nothing`() =
        runTest(mainRule.dispatcher) {
            seed()
            store(row("e"), row("i", TransactionType.INCOME))
            val vm = open()

            vm.onIntent(TransactionListIntent.RowClicked("i"))
            assertNull(vm.state.value.editingId)
            vm.onIntent(TransactionListIntent.RowClicked("e"))

            assertEquals("e", vm.state.value.editingId)
        }

    @Test
    fun `picking a choice writes the fields and closes the sheet`() =
        runTest(mainRule.dispatcher) {
            seed()
            store(row("e"))
            val vm = open()
            vm.onIntent(TransactionListIntent.RowClicked("e"))

            vm.onIntent(TransactionListIntent.ReimbursementPicked(ReimbursementChoice.PENDING))
            runCurrent()

            val saved = fakeTx.stored.getValue("e")
            assertEquals(ReimbursementStatus.PENDING, saved.reimbursementStatus)
            assertTrue(saved.isExcludedFromAnalytics)
            assertNull(vm.state.value.editingId)
            assertFalse(vm.state.value.hasSaveError)

            // The fake does not re-emit on a write, so feed the saved row back as the DB would.
            fakeTx.transactionsFlow.value = listOf(saved)
            runCurrent()
            vm.onIntent(TransactionListIntent.RowClicked("e"))
            vm.onIntent(TransactionListIntent.ReimbursementPicked(ReimbursementChoice.NORMAL))
            runCurrent()

            assertEquals(ReimbursementStatus.NONE, fakeTx.stored.getValue("e").reimbursementStatus)
            assertFalse(fakeTx.stored.getValue("e").isExcludedFromAnalytics)
        }

    @Test
    fun `picking the current choice writes nothing and closes the sheet`() =
        runTest(mainRule.dispatcher) {
            seed()
            store(row("e"))
            val vm = open()
            vm.onIntent(TransactionListIntent.RowClicked("e"))

            vm.onIntent(TransactionListIntent.ReimbursementPicked(ReimbursementChoice.NORMAL))
            runCurrent()

            assertEquals(0, fakeTx.writes)
            assertNull(vm.state.value.editingId)
        }

    @Test
    fun `a failed write keeps the sheet open with an error until it is dismissed`() =
        runTest(mainRule.dispatcher) {
            seed()
            store(row("e"))
            fakeTx.stored.remove("e") // the use case rejects a transaction it cannot find
            val vm = open()
            vm.onIntent(TransactionListIntent.RowClicked("e"))

            vm.onIntent(TransactionListIntent.ReimbursementPicked(ReimbursementChoice.SETTLED))
            runCurrent()

            assertEquals("e", vm.state.value.editingId)
            assertTrue(vm.state.value.hasSaveError)
            assertEquals(0, fakeTx.writes)

            // A new attempt clears the error first, so a repeated failure is announced again.
            vm.onIntent(TransactionListIntent.ReimbursementPicked(ReimbursementChoice.PENDING))
            assertFalse(vm.state.value.hasSaveError)
            runCurrent()
            assertTrue(vm.state.value.hasSaveError)

            vm.onIntent(TransactionListIntent.SheetDismissed)

            assertNull(vm.state.value.editingId)
            assertFalse(vm.state.value.hasSaveError)
        }

    @Test
    fun `storage failure on reimbursement write shows save error`() =
        runTest(mainRule.dispatcher) {
            seed()
            store(row("e"))
            val vm = open()
            vm.onIntent(TransactionListIntent.RowClicked("e"))
            fakeTx.failUpdate = StorageException(RuntimeException("disk"))

            vm.onIntent(TransactionListIntent.ReimbursementPicked(ReimbursementChoice.PENDING))
            runCurrent()

            assertEquals("e", vm.state.value.editingId)
            assertTrue(vm.state.value.hasSaveError)
        }

    @Test
    fun `an odd excluded pair without a status is cleared by picking normal`() =
        runTest(mainRule.dispatcher) {
            seed()
            store(row("e").copy(isExcludedFromAnalytics = true))
            val vm = open()
            vm.onIntent(TransactionListIntent.RowClicked("e"))

            vm.onIntent(TransactionListIntent.ReimbursementPicked(ReimbursementChoice.NORMAL))
            runCurrent()

            assertEquals(1, fakeTx.writes)
            assertFalse(fakeTx.stored.getValue("e").isExcludedFromAnalytics)
            assertEquals(ReimbursementStatus.NONE, fakeTx.stored.getValue("e").reimbursementStatus)
            assertNull(vm.state.value.editingId)
        }

    @Test
    fun `a pick while a write is in flight is ignored`() =
        runTest(mainRule.dispatcher) {
            seed()
            store(row("e"))
            val vm = open()
            vm.onIntent(TransactionListIntent.RowClicked("e"))

            vm.onIntent(TransactionListIntent.ReimbursementPicked(ReimbursementChoice.PENDING))
            vm.onIntent(TransactionListIntent.ReimbursementPicked(ReimbursementChoice.SETTLED))
            runCurrent()

            assertEquals(1, fakeTx.writes)
            assertEquals(ReimbursementStatus.PENDING, fakeTx.stored.getValue("e").reimbursementStatus)
        }

    @Test
    fun `an expense on an archived account does not open the sheet`() =
        runTest(mainRule.dispatcher) {
            seed()
            store(row("e", account = B))
            val vm = open()

            vm.onIntent(TransactionListIntent.RowClicked("e"))

            assertNull(vm.state.value.editingId)
        }

    @Test
    fun `the sheet closes when its row leaves the list`() =
        runTest(mainRule.dispatcher) {
            seed()
            store(row("e"), row("other"))
            val vm = open()
            vm.onIntent(TransactionListIntent.RowClicked("e"))
            assertEquals("e", vm.state.value.editingId)

            fakeTx.transactionsFlow.value = listOf(row("other"))
            runCurrent()

            assertNull(vm.state.value.editingId)
            assertFalse(vm.state.value.hasSaveError)
        }

    @Test
    fun `the sheet closes when the list fails`() =
        runTest(mainRule.dispatcher) {
            seed()
            accounts.stored[C] = testAccount(C)
            store(row("e"))
            val vm = open()
            vm.onIntent(TransactionListIntent.RowClicked("e"))

            fakeTx.failObserve = StorageException(SQLiteException("disk"))
            vm.onIntent(TransactionListIntent.SelectAccount(C))
            runCurrent()

            assertTrue(vm.state.value.hasError)
            assertNull(vm.state.value.editingId)
        }

    @Test
    fun `pending chip clears the account and an account clears the pending chip`() =
        runTest(mainRule.dispatcher) {
            seed()
            store(
                row("plain"),
                row("wait", status = ReimbursementStatus.PENDING),
                row("done", status = ReimbursementStatus.SETTLED),
            )
            val vm = open()
            vm.onIntent(TransactionListIntent.SelectAccount(A))
            runCurrent()

            vm.onIntent(TransactionListIntent.SelectPending)
            runCurrent()

            assertTrue(vm.state.value.isPendingOnly)
            assertNull(vm.state.value.selectedAccountId)
            assertEquals(
                listOf("wait"),
                vm.state.value.days
                    .flatMap { it.transactions }
                    .map { it.id },
            )

            vm.onIntent(TransactionListIntent.SelectAccount(A))
            runCurrent()

            assertFalse(vm.state.value.isPendingOnly)
            assertEquals(A, vm.state.value.selectedAccountId)
            assertEquals(
                3,
                vm.state.value.days
                    .sumOf { it.transactions.size },
            )
        }

    @Test
    fun `pending mode survives in SavedStateHandle`() =
        runTest(mainRule.dispatcher) {
            seed()
            store(row("plain"), row("wait", status = ReimbursementStatus.PENDING))

            val vm = open(SavedStateHandle(mapOf("pending_only" to true)))

            assertTrue(vm.state.value.isPendingOnly)
            assertEquals(
                listOf("wait"),
                vm.state.value.days
                    .flatMap { it.transactions }
                    .map { it.id },
            )
        }

    @Test
    fun `drifts reach the state`() =
        runTest(mainRule.dispatcher) {
            seed()
            store(row("e").copy(type = TransactionType.INCOME, postBalance = 1_700_000))

            val vm = open()

            assertEquals(listOf(BalanceDrift(A, 700_000 - 10_000)), vm.state.value.drifts)
        }

    @Test
    fun `ignored account has no drift`() =
        runTest(mainRule.dispatcher) {
            seed()
            accounts.stored[A] = checkNotNull(accounts.stored[A]).copy(isIgnored = true)
            store(row("e").copy(type = TransactionType.INCOME, postBalance = 1_700_000))

            val vm = open()

            assertTrue(
                vm.state.value.drifts
                    .isEmpty(),
            )
        }

    @Test
    fun `an ignored account is not a chip but is still named`() =
        runTest(mainRule.dispatcher) {
            seed()
            accounts.stored[C] = testAccount(C).copy(isIgnored = true)

            val vm = open()

            assertEquals(
                listOf(A),
                vm.state.value.accounts
                    .map { it.id },
            )
            assertTrue(C in vm.state.value.accountsById)
        }

    @Test
    fun `adjust leaves the drift check to the repository`() =
        runTest(mainRule.dispatcher) {
            seed()
            val vm = open()

            vm.onIntent(TransactionListIntent.AdjustDrift(A))
            runCurrent()

            assertEquals(A, fakeTx.adjustCalls.single().accountId)
        }
}

private const val ROW_AT = 1_793_000_000_000L // 2026-10-26, inside the validator's range for the test clock

private class MutableClock(
    var now: Instant,
    private val zone: ZoneId,
) : Clock() {
    override fun getZone(): ZoneId = zone

    override fun withZone(zone: ZoneId): Clock = MutableClock(now, zone)

    override fun instant(): Instant = now
}
