package io.github.toanhp123.finote.ui.transactions

import androidx.lifecycle.SavedStateHandle
import androidx.sqlite.SQLiteException
import app.cash.turbine.test
import io.github.toanhp123.finote.domain.account.A
import io.github.toanhp123.finote.domain.account.B
import io.github.toanhp123.finote.domain.account.C
import io.github.toanhp123.finote.domain.account.adjustment
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
import io.github.toanhp123.finote.domain.transaction.model.AdjustmentDirection
import io.github.toanhp123.finote.domain.transaction.model.ReimbursementStatus
import io.github.toanhp123.finote.domain.transaction.model.Transaction
import io.github.toanhp123.finote.domain.transaction.model.TransactionType
import io.github.toanhp123.finote.domain.transaction.usecase.AdjustBalanceDriftUseCase
import io.github.toanhp123.finote.domain.transaction.usecase.DeleteTransactionUseCase
import io.github.toanhp123.finote.domain.transaction.usecase.FakeTransactionRepository
import io.github.toanhp123.finote.domain.transaction.usecase.FakeTransferPairRepository
import io.github.toanhp123.finote.domain.transaction.usecase.ObserveAutoPairedIdsUseCase
import io.github.toanhp123.finote.domain.transaction.usecase.ObserveTransactionListUseCase
import io.github.toanhp123.finote.ui.common.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
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
            observeDrifts =
                ObserveBalanceDriftUseCase(ObserveUsableAccountsUseCase(ObserveAccountsUseCase(accounts)), fakeTx),
            adjustBalanceDrift = AdjustBalanceDriftUseCase(fakeTx, FakeTransactionRunner(), clock),
            deleteTransaction = DeleteTransactionUseCase(fakeTx, FakeTransactionRunner(), clock),
            observeAutoPairedIds = ObserveAutoPairedIdsUseCase(FakeTransferPairRepository(fakeTx)),
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
    fun `a transfer made by pairing is in autoPairedIds`() =
        runTest(mainRule.dispatcher) {
            seed()
            val transfer = tx(TransactionType.TRANSFER, 10_000, to = B).copy(id = "t1")
            val original = tx(TransactionType.EXPENSE, 10_000).copy(id = "o1", deletedAt = 5L, pairedIntoId = "t1")
            fakeTx.transactionsFlow.value = listOf(transfer, original)

            val vm = open()

            assertEquals(setOf("t1"), vm.state.value.autoPairedIds)
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
        status: ReimbursementStatus = ReimbursementStatus.NONE,
    ): Transaction =
        tx(type, 10_000, at = ROW_AT)
            .copy(id = id, reimbursementStatus = status, isExcludedFromAnalytics = status != ReimbursementStatus.NONE)

    private fun store(vararg rows: Transaction) {
        rows.forEach { fakeTx.stored[it.id] = it }
        fakeTx.transactionsFlow.value = rows.toList()
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

    private fun TestScope.click(
        vm: TransactionListViewModel,
        id: String,
    ) {
        vm.onIntent(TransactionListIntent.RowClicked(id))
        runCurrent()
    }

    @Test
    fun `a click on an expense, an income and a transfer sends OpenEditor`() =
        runTest(mainRule.dispatcher) {
            seed()
            accounts.stored[C] = testAccount(C)
            store(
                row("e"),
                row("i", TransactionType.INCOME),
                tx(TransactionType.TRANSFER, 10_000, to = C, at = ROW_AT).copy(id = "t"),
            )
            val vm = open()

            vm.effects.test {
                listOf("e", "i", "t").forEach {
                    click(vm, it)
                    assertEquals(TransactionListEffect.OpenEditor(it), awaitItem())
                }
            }
        }

    @Test
    fun `a row on an archived account sends nothing`() =
        runTest(mainRule.dispatcher) {
            seed()
            store(tx(TransactionType.EXPENSE, 10_000, account = B, at = ROW_AT).copy(id = "old"))
            val vm = open()

            vm.effects.test {
                click(vm, "old")
                expectNoEvents()
            }
            assertNull(vm.state.value.deletingId)
        }

    @Test
    fun `a transfer whose destination is archived sends nothing`() =
        runTest(mainRule.dispatcher) {
            seed()
            store(tx(TransactionType.TRANSFER, 10_000, to = B, at = ROW_AT).copy(id = "t"))
            val vm = open()

            vm.effects.test {
                click(vm, "t")
                expectNoEvents()
            }
        }

    @Test
    fun `a click on an adjustment opens the confirm and sends no effect`() =
        runTest(mainRule.dispatcher) {
            seed()
            store(adjustment(AdjustmentDirection.OUT, 10_000, at = ROW_AT).copy(id = "adj"))
            val vm = open()

            vm.effects.test {
                click(vm, "adj")
                expectNoEvents()
            }
            assertEquals("adj", vm.state.value.deletingId)
        }

    @Test
    fun `confirm deletes the adjustment`() =
        runTest(mainRule.dispatcher) {
            seed()
            store(adjustment(AdjustmentDirection.OUT, 10_000, at = ROW_AT).copy(id = "adj"))
            val vm = open()
            click(vm, "adj")

            vm.onIntent(TransactionListIntent.DeleteConfirmed)
            runCurrent()

            assertNull(vm.state.value.deletingId)
            assertNotNull(fakeTx.stored.getValue("adj").deletedAt)
        }

    @Test
    fun `a failed delete sends DeleteFailed`() =
        runTest(mainRule.dispatcher) {
            seed()
            store(adjustment(AdjustmentDirection.OUT, 10_000, at = ROW_AT).copy(id = "adj"))
            fakeTx.failWrite = StorageException(SQLiteException("disk"))
            val vm = open()
            click(vm, "adj")

            vm.effects.test {
                vm.onIntent(TransactionListIntent.DeleteConfirmed)
                runCurrent()
                assertEquals(TransactionListEffect.DeleteFailed, awaitItem())
            }
            assertNull(vm.state.value.deletingId)
        }

    @Test
    fun `a delete the use case refuses sends DeleteFailed`() =
        runTest(mainRule.dispatcher) {
            seed()
            // In the list but not in the store: the use case answers NotFound.
            fakeTx.transactionsFlow.value =
                listOf(adjustment(AdjustmentDirection.OUT, 10_000, at = ROW_AT).copy(id = "adj"))
            val vm = open()
            click(vm, "adj")

            vm.effects.test {
                vm.onIntent(TransactionListIntent.DeleteConfirmed)
                runCurrent()
                assertEquals(TransactionListEffect.DeleteFailed, awaitItem())
            }
        }

    @Test
    fun `dismiss clears deletingId`() =
        runTest(mainRule.dispatcher) {
            seed()
            store(adjustment(AdjustmentDirection.OUT, 10_000, at = ROW_AT).copy(id = "adj"))
            val vm = open()
            click(vm, "adj")

            vm.onIntent(TransactionListIntent.DeleteDismissed)

            assertNull(vm.state.value.deletingId)
            assertNull(fakeTx.stored.getValue("adj").deletedAt)
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
