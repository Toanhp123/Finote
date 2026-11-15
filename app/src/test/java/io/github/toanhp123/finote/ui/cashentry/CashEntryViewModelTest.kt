package io.github.toanhp123.finote.ui.cashentry

import io.github.toanhp123.finote.domain.account.testAccount
import io.github.toanhp123.finote.domain.account.usecase.FakeAccountRepository
import io.github.toanhp123.finote.domain.account.usecase.ObserveAccountsUseCase
import io.github.toanhp123.finote.domain.account.usecase.ObserveUsableAccountsUseCase
import io.github.toanhp123.finote.domain.category.model.CategoryType
import io.github.toanhp123.finote.domain.category.testCategory
import io.github.toanhp123.finote.domain.category.usecase.FakeCategoryRepository
import io.github.toanhp123.finote.domain.category.usecase.ObserveCategoriesUseCase
import io.github.toanhp123.finote.domain.common.FakeTransactionRunner
import io.github.toanhp123.finote.domain.common.StorageException
import io.github.toanhp123.finote.domain.transaction.model.Transaction
import io.github.toanhp123.finote.domain.transaction.model.TransactionSource
import io.github.toanhp123.finote.domain.transaction.model.TransactionType
import io.github.toanhp123.finote.domain.transaction.repository.TransactionRepository
import io.github.toanhp123.finote.domain.transaction.usecase.AddManualTransactionUseCase
import io.github.toanhp123.finote.domain.transaction.usecase.AddTransactionUseCase
import io.github.toanhp123.finote.domain.transaction.usecase.FakeTransactionRepository
import io.github.toanhp123.finote.domain.transaction.usecase.GetLastManualChoicesUseCase
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
class CashEntryViewModelTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    private val clock = Clock.fixed(Instant.parse("2026-11-04T03:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"))
    private val accounts = FakeAccountRepository()
    private val categories = FakeCategoryRepository()
    private val transactions =
        FakeTransactionRepository(
            activeAccounts = setOf(CASH, BANK),
            categories = mapOf("c1" to CategoryType.EXPENSE, "c2" to CategoryType.EXPENSE, "i1" to CategoryType.INCOME),
        )

    private fun seed() {
        accounts.stored[CASH] = testAccount(CASH)
        accounts.stored[BANK] = testAccount(BANK).copy(sortOrder = 1)
        listOf(
            testCategory("c1"),
            testCategory("c2"),
            testCategory("i1", type = CategoryType.INCOME),
        ).forEach { categories.stored[it.id] = it }
    }

    private fun TestScope.open(repo: TransactionRepository = transactions): CashEntryViewModel {
        val add = AddTransactionUseCase(repo, FakeTransactionRunner(), clock)
        return CashEntryViewModel(
            ObserveUsableAccountsUseCase(ObserveAccountsUseCase(accounts)),
            ObserveCategoriesUseCase(categories),
            GetLastManualChoicesUseCase(repo),
            AddManualTransactionUseCase(add, clock),
            clock,
            isDebuggable = true,
        ).also { runCurrent() }
    }

    private fun CashEntryViewModel.press(vararg keys: CalcKey) =
        keys.forEach { onIntent(CashEntryIntent.KeyPressed(it)) }

    private val CashEntryViewModel.current get() = state.value

    @Test
    fun `opens with last used account and categories`() =
        runTest(mainRule.dispatcher) {
            seed()
            transactions.lastAccountId = BANK
            transactions.lastCategoryIds[TransactionType.EXPENSE] = "c1"
            transactions.lastCategoryIds[TransactionType.INCOME] = "i1"

            val s = open().current

            assertEquals(TransactionType.EXPENSE, s.type)
            assertEquals(LocalDate.of(2026, 11, 4), s.date)
            assertTrue(s.isToday)
            assertEquals(BANK, s.selectedAccountId)
            assertEquals(listOf(CASH, BANK), s.accounts.map { it.id })
            assertEquals(mapOf(TransactionType.EXPENSE to "c1", TransactionType.INCOME to "i1"), s.selectedCategoryIds)
            assertEquals(listOf("c1", "c2"), s.categories.map { it.id })
        }

    @Test
    fun `empty accounts then seeded account selects it`() =
        runTest(mainRule.dispatcher) {
            val viewModel = open()
            assertNull(viewModel.current.selectedAccountId)

            accounts.add(testAccount(CASH))
            runCurrent()

            assertEquals(CASH, viewModel.current.selectedAccountId)
        }

    @Test
    fun `selected account archived reselects default`() =
        runTest(mainRule.dispatcher) {
            seed()
            transactions.lastAccountId = BANK
            val viewModel = open()
            assertEquals(BANK, viewModel.current.selectedAccountId)

            accounts.setArchived(BANK, archived = true, updatedAt = 2L)
            runCurrent()

            assertEquals(CASH, viewModel.current.selectedAccountId)
            assertEquals(listOf(CASH), viewModel.current.accounts.map { it.id })
        }

    @Test
    fun `ignored account is not offered`() =
        runTest(mainRule.dispatcher) {
            seed()
            accounts.stored[BANK] = checkNotNull(accounts.stored[BANK]).copy(isIgnored = true)
            transactions.lastAccountId = BANK

            val s = open().current

            assertEquals(listOf(CASH), s.accounts.map { it.id })
            assertEquals(CASH, s.selectedAccountId)
        }

    @Test
    fun `category choice survives type switch`() =
        runTest(mainRule.dispatcher) {
            seed()
            val viewModel = open()

            viewModel.onIntent(CashEntryIntent.CategoryPicked("c2"))
            viewModel.onIntent(CashEntryIntent.TypeChanged(TransactionType.INCOME))
            assertEquals(listOf("i1"), viewModel.current.categories.map { it.id })
            viewModel.onIntent(CashEntryIntent.TypeChanged(TransactionType.EXPENSE))

            assertEquals("c2", viewModel.current.selectedCategoryIds[TransactionType.EXPENSE])
        }

    @Test
    fun `category picked null clears it`() =
        runTest(mainRule.dispatcher) {
            seed()
            transactions.lastCategoryIds[TransactionType.EXPENSE] = "c1"
            val viewModel = open()

            viewModel.onIntent(CashEntryIntent.CategoryPicked(null))

            assertNull(viewModel.current.selectedCategoryIds[TransactionType.EXPENSE])
        }

    @Test
    fun `account picked outside the live list is ignored`() =
        runTest(mainRule.dispatcher) {
            seed()
            val viewModel = open()
            viewModel.onIntent(CashEntryIntent.PageOpened(CashEntryPage.ACCOUNT))

            viewModel.onIntent(CashEntryIntent.AccountPicked("ghost"))

            assertEquals(CASH, viewModel.current.selectedAccountId)
            assertEquals(CashEntryPage.ACCOUNT, viewModel.current.page)

            viewModel.onIntent(CashEntryIntent.AccountPicked(BANK))

            assertEquals(BANK, viewModel.current.selectedAccountId)
            assertEquals(CashEntryPage.KEYPAD, viewModel.current.page)
        }

    @Test
    fun `hidden category is not offered and not kept`() =
        runTest(mainRule.dispatcher) {
            seed()
            transactions.lastCategoryIds[TransactionType.EXPENSE] = "c1"
            val viewModel = open()

            categories.setHidden("c1", hidden = true, updatedAt = 2L)
            runCurrent()

            assertEquals(listOf("c2"), viewModel.current.categories.map { it.id })
            assertNull(viewModel.current.selectedCategoryIds[TransactionType.EXPENSE])
        }

    @Test
    fun `not positive shows no error text and disables save`() =
        runTest(mainRule.dispatcher) {
            seed()
            val viewModel = open()

            viewModel.press(CalcKey.D1, CalcKey.DIVIDE, CalcKey.D3)

            assertTrue(viewModel.current.result is AmountResult.NotPositive)
            assertFalse(viewModel.current.isSaveEnabled)
        }

    @Test
    fun `page and note intents`() =
        runTest(mainRule.dispatcher) {
            seed()
            val viewModel = open()

            viewModel.onIntent(CashEntryIntent.PageOpened(CashEntryPage.NOTE))
            assertEquals(CashEntryPage.NOTE, viewModel.current.page)
            viewModel.onIntent(CashEntryIntent.NoteChanged("x".repeat(250)))
            assertEquals(200, viewModel.current.note.length)
            viewModel.onIntent(CashEntryIntent.PageClosed)
            assertEquals(CashEntryPage.KEYPAD, viewModel.current.page)
            viewModel.onIntent(CashEntryIntent.PageOpened(CashEntryPage.ACCOUNT))
            viewModel.onIntent(CashEntryIntent.AccountPicked(BANK))
            assertEquals(BANK, viewModel.current.selectedAccountId)
            assertEquals(CashEntryPage.KEYPAD, viewModel.current.page)
        }

    @Test
    fun `save today uses clock millis and closes`() =
        runTest(mainRule.dispatcher) {
            seed()
            transactions.lastCategoryIds[TransactionType.EXPENSE] = "c1"
            val viewModel = open()
            viewModel.press(CalcKey.D5, CalcKey.D0, CalcKey.TRIPLE_ZERO)
            viewModel.onIntent(CashEntryIntent.NoteChanged("  lunch "))

            viewModel.onIntent(CashEntryIntent.SaveClicked)
            runCurrent()

            val saved = transactions.stored.values.single()
            assertEquals(50_000L, saved.amount)
            assertEquals(TransactionType.EXPENSE, saved.type)
            assertEquals(CASH, saved.accountId)
            assertEquals("c1", saved.categoryId)
            assertEquals("lunch", saved.note)
            assertEquals(TransactionSource.MANUAL, saved.source)
            assertEquals(clock.millis(), saved.timestamp)
            assertFalse(viewModel.current.isVisible)
        }

    @Test
    fun `dismiss hides the sheet`() =
        runTest(mainRule.dispatcher) {
            seed()
            val viewModel = open()
            assertTrue(viewModel.current.isVisible)

            viewModel.onIntent(CashEntryIntent.Dismissed)

            assertFalse(viewModel.current.isVisible)
        }

    @Test
    fun `save after a successful save does nothing`() =
        runTest(mainRule.dispatcher) {
            seed()
            val viewModel = open()
            viewModel.press(CalcKey.D5)
            viewModel.onIntent(CashEntryIntent.SaveClicked)
            runCurrent()

            viewModel.onIntent(CashEntryIntent.SaveClicked)
            runCurrent()

            assertEquals(1, transactions.stored.size)
        }

    @Test
    fun `picking a past date clears isToday and picking today sets it`() =
        runTest(mainRule.dispatcher) {
            seed()
            val viewModel = open()

            viewModel.onIntent(CashEntryIntent.DatePicked(LocalDate.of(2026, 10, 1)))
            assertFalse(viewModel.current.isToday)

            viewModel.onIntent(CashEntryIntent.DatePicked(LocalDate.of(2026, 11, 4)))
            assertTrue(viewModel.current.isToday)
        }

    @Test
    fun `date page then DatePicked sets the date and returns to the keypad`() =
        runTest(mainRule.dispatcher) {
            seed()
            val viewModel = open()
            viewModel.onIntent(CashEntryIntent.PageOpened(CashEntryPage.DATE))
            assertEquals(CashEntryPage.DATE, viewModel.current.page)

            viewModel.onIntent(CashEntryIntent.DatePicked(LocalDate.of(2026, 10, 1)))

            assertEquals(LocalDate.of(2026, 10, 1), viewModel.current.date)
            assertEquals(CashEntryPage.KEYPAD, viewModel.current.page)
        }

    @Test
    fun `save on past date uses noon local time`() =
        runTest(mainRule.dispatcher) {
            seed()
            val viewModel = open()
            viewModel.press(CalcKey.D9)
            viewModel.onIntent(CashEntryIntent.DatePicked(LocalDate.of(2026, 10, 1)))

            viewModel.onIntent(CashEntryIntent.SaveClicked)
            runCurrent()

            val expected = Instant.parse("2026-10-01T12:00:00+07:00").toEpochMilli()
            assertEquals(
                expected,
                transactions.stored.values
                    .single()
                    .timestamp,
            )
        }

    @Test
    fun `save failure keeps input`() =
        runTest(mainRule.dispatcher) {
            seed()
            val noActiveAccount = FakeTransactionRepository()
            val viewModel = open(noActiveAccount)
            viewModel.press(CalcKey.D7, CalcKey.D0)

            viewModel.onIntent(CashEntryIntent.SaveClicked)
            runCurrent()

            assertTrue(viewModel.current.hasSaveError)
            assertFalse(viewModel.current.isSaving)
            assertEquals("70", viewModel.current.expression)
            assertTrue(noActiveAccount.stored.isEmpty())
            assertTrue(viewModel.current.isVisible)
        }

    @Test
    fun `storage failure on save shows save error`() =
        runTest(mainRule.dispatcher) {
            seed()
            val failing =
                FailingAddRepository(StorageException(RuntimeException("disk")), FakeTransactionRepository(setOf(CASH)))
            val viewModel = open(failing)
            viewModel.press(CalcKey.D7, CalcKey.D0)

            viewModel.onIntent(CashEntryIntent.SaveClicked)
            runCurrent()

            assertTrue(viewModel.current.hasSaveError)
            assertFalse(viewModel.current.isSaving)
            assertEquals("70", viewModel.current.expression)
            assertTrue(viewModel.current.isVisible)
        }

    @Test
    fun `failing last-choice read leaves empty lists and save disabled`() =
        runTest(mainRule.dispatcher) {
            seed()
            transactions.failLastChoice = StorageException(RuntimeException("disk"))
            val viewModel = open()
            viewModel.press(CalcKey.D5)

            assertTrue(viewModel.current.accounts.isEmpty())
            assertFalse(viewModel.current.isSaveEnabled)
        }

    @Test
    fun `key press after save failure clears the error`() =
        runTest(mainRule.dispatcher) {
            seed()
            val viewModel = open(FakeTransactionRepository())
            viewModel.press(CalcKey.D7)
            viewModel.onIntent(CashEntryIntent.SaveClicked)
            runCurrent()
            assertTrue(viewModel.current.hasSaveError)

            viewModel.press(CalcKey.D0)

            assertFalse(viewModel.current.hasSaveError)
            assertEquals("70", viewModel.current.expression)
        }

    private fun CashEntryViewModel.openTransfer(destination: String? = BANK) {
        onIntent(CashEntryIntent.TypeChanged(TransactionType.TRANSFER))
        destination?.let { onIntent(CashEntryIntent.DestinationPicked(it)) }
    }

    @Test
    fun `saving a transfer sends destination and fee and no category`() =
        runTest(mainRule.dispatcher) {
            seed()
            transactions.lastCategoryIds[TransactionType.EXPENSE] = "c1"
            val viewModel = open()
            viewModel.press(CalcKey.D5, CalcKey.D0, CalcKey.TRIPLE_ZERO)
            viewModel.openTransfer()
            viewModel.onIntent(CashEntryIntent.FeeChanged("1500"))

            viewModel.onIntent(CashEntryIntent.SaveClicked)
            runCurrent()

            val saved = transactions.stored.values.single()
            assertEquals(TransactionType.TRANSFER, saved.type)
            assertEquals(50_000L, saved.amount)
            assertEquals(CASH, saved.accountId)
            assertEquals(BANK, saved.toAccountId)
            assertEquals(1_500L, saved.feeAmount)
            assertNull(saved.categoryId)
            assertFalse(viewModel.current.isVisible)
        }

    @Test
    fun `a transfer cannot be saved without a destination`() =
        runTest(mainRule.dispatcher) {
            seed()
            val viewModel = open()
            viewModel.press(CalcKey.D5)
            viewModel.openTransfer(destination = null)

            assertFalse(viewModel.current.isSaveEnabled)
            viewModel.onIntent(CashEntryIntent.SaveClicked)
            runCurrent()
            assertTrue(transactions.stored.isEmpty())

            viewModel.onIntent(CashEntryIntent.DestinationPicked(BANK))
            assertTrue(viewModel.current.isSaveEnabled)
        }

    @Test
    fun `destination choices skip the source and a pick of the source is ignored`() =
        runTest(mainRule.dispatcher) {
            seed()
            val viewModel = open()
            viewModel.openTransfer(destination = null)
            viewModel.onIntent(CashEntryIntent.PageOpened(CashEntryPage.DESTINATION))

            viewModel.onIntent(CashEntryIntent.DestinationPicked(CASH))

            assertEquals(listOf(BANK), viewModel.current.destinationChoices.map { it.id })
            assertNull(viewModel.current.destinationId)
            assertEquals(CashEntryPage.DESTINATION, viewModel.current.page)
        }

    @Test
    fun `picking the destination as source clears the destination`() =
        runTest(mainRule.dispatcher) {
            seed()
            val viewModel = open()
            viewModel.openTransfer()
            assertEquals(BANK, viewModel.current.destinationId)

            viewModel.onIntent(CashEntryIntent.AccountPicked(BANK))

            assertEquals(BANK, viewModel.current.selectedAccountId)
            assertNull(viewModel.current.destinationId)
        }

    @Test
    fun `a destination that leaves the live list is cleared`() =
        runTest(mainRule.dispatcher) {
            seed()
            val viewModel = open()
            viewModel.openTransfer()

            accounts.setArchived(BANK, archived = true, updatedAt = 2L)
            runCurrent()

            assertNull(viewModel.current.destinationId)
        }

    @Test
    fun `source leaving the list falls back to the destination and clears it`() =
        runTest(mainRule.dispatcher) {
            seed()
            val viewModel = open()
            viewModel.openTransfer()
            assertEquals(CASH, viewModel.current.selectedAccountId)
            assertEquals(BANK, viewModel.current.destinationId)

            accounts.setArchived(CASH, archived = true, updatedAt = 2L)
            runCurrent()

            assertEquals(BANK, viewModel.current.selectedAccountId)
            assertNull(viewModel.current.destinationId)
        }

    @Test
    fun `fee keeps digits only and at most thirteen of them`() =
        runTest(mainRule.dispatcher) {
            seed()
            val viewModel = open()

            viewModel.onIntent(CashEntryIntent.FeeChanged("1a2.3-4"))
            assertEquals(1_234L, viewModel.current.fee)

            viewModel.onIntent(CashEntryIntent.FeeChanged("9".repeat(20)))
            assertEquals(9_999_999_999_999L, viewModel.current.fee)

            viewModel.onIntent(CashEntryIntent.FeeChanged("x"))
            assertEquals(0L, viewModel.current.fee)
        }

    @Test
    fun `amount plus fee overflow shows the save error and keeps the sheet`() =
        runTest(mainRule.dispatcher) {
            seed()
            val viewModel = open()
            // 3037000499 squared is just below Long.MAX_VALUE, so any 13-digit fee overflows the sum.
            val side = "3037000499".map { CalcKey.valueOf("D$it") }
            viewModel.press(*side.toTypedArray(), CalcKey.TIMES, *side.toTypedArray())
            viewModel.openTransfer()
            viewModel.onIntent(CashEntryIntent.FeeChanged("9".repeat(20)))
            assertTrue(viewModel.current.result is AmountResult.Ok)

            viewModel.onIntent(CashEntryIntent.SaveClicked)
            runCurrent()

            assertTrue(viewModel.current.hasSaveError)
            assertFalse(viewModel.current.isSaving)
            assertTrue(viewModel.current.isVisible)
            assertTrue(transactions.stored.isEmpty())
        }

    @Test
    fun `type switch keeps the entry, and a non-transfer drops destination and fee`() =
        runTest(mainRule.dispatcher) {
            seed()
            val viewModel = open()
            viewModel.press(CalcKey.D7)
            viewModel.onIntent(CashEntryIntent.NoteChanged("n"))
            viewModel.onIntent(CashEntryIntent.DatePicked(LocalDate.of(2026, 10, 1)))
            viewModel.openTransfer()
            viewModel.onIntent(CashEntryIntent.FeeChanged("500"))

            viewModel.onIntent(CashEntryIntent.TypeChanged(TransactionType.INCOME))

            val s = viewModel.current
            assertEquals("7", s.expression)
            assertEquals("n", s.note)
            assertEquals(LocalDate.of(2026, 10, 1), s.date)
            assertEquals(CASH, s.selectedAccountId)

            viewModel.onIntent(CashEntryIntent.SaveClicked)
            runCurrent()

            val saved = transactions.stored.values.single()
            assertEquals(TransactionType.INCOME, saved.type)
            assertNull(saved.toAccountId)
            assertEquals(0L, saved.feeAmount)
        }

    private companion object {
        const val CASH = "sys_cash"
        const val BANK = "acc-1"
    }
}

/** A repository whose write always fails with [error]; everything else is delegated. */
private class FailingAddRepository(
    private val error: Throwable,
    delegate: TransactionRepository,
) : TransactionRepository by delegate {
    override suspend fun add(transaction: Transaction): Unit = throw error
}
