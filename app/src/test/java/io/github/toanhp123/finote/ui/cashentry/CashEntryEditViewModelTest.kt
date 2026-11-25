package io.github.toanhp123.finote.ui.cashentry

import app.cash.turbine.test
import io.github.toanhp123.finote.domain.account.testAccount
import io.github.toanhp123.finote.domain.account.usecase.FakeAccountRepository
import io.github.toanhp123.finote.domain.category.model.CategoryType
import io.github.toanhp123.finote.domain.category.testCategory
import io.github.toanhp123.finote.domain.category.usecase.FakeCategoryRepository
import io.github.toanhp123.finote.domain.common.StorageException
import io.github.toanhp123.finote.domain.transaction.model.AdjustmentDirection
import io.github.toanhp123.finote.domain.transaction.model.ReimbursementStatus
import io.github.toanhp123.finote.domain.transaction.model.Transaction
import io.github.toanhp123.finote.domain.transaction.model.TransactionSource
import io.github.toanhp123.finote.domain.transaction.model.TransactionType
import io.github.toanhp123.finote.domain.transaction.repository.TransactionRepository
import io.github.toanhp123.finote.domain.transaction.usecase.FakeTransactionRepository
import io.github.toanhp123.finote.domain.transaction.usecase.FakeTransferPairRepository
import io.github.toanhp123.finote.ui.cashentry.logic.AmountResult
import io.github.toanhp123.finote.ui.cashentry.logic.CalcKey
import io.github.toanhp123.finote.ui.cashentry.logic.ReimbursementChoice
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

/** The edit mode of the cash-entry sheet: a stored row fills the form, save updates, delete is a soft delete. */
@OptIn(ExperimentalCoroutinesApi::class)
class CashEntryEditViewModelTest {
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

    private val pairs = FakeTransferPairRepository(transactions)

    private fun TestScope.open(
        repo: TransactionRepository = transactions,
        transactionId: String? = ROW_ID,
    ): CashEntryViewModel =
        cashEntryViewModel(transactionId, accounts, categories, repo, clock, pairs).also { runCurrent() }

    private fun CashEntryViewModel.press(vararg keys: CalcKey) =
        keys.forEach { onIntent(CashEntryIntent.KeyPressed(it)) }

    private val CashEntryViewModel.current get() = state.value

    private fun TestScope.openEdit(repo: TransactionRepository = transactions) = open(repo)

    /** Stores a row for [ROW_ID] on the cash account, dated today, and returns it. */
    private fun row(
        type: TransactionType = TransactionType.EXPENSE,
        amount: Long = 50_000L,
        to: String? = null,
        fee: Long = 0L,
        categoryId: String? = null,
        note: String = "",
    ) = Transaction(
        id = ROW_ID,
        amount = amount,
        feeAmount = fee,
        type = type,
        adjustmentDirection = null,
        accountId = CASH,
        toAccountId = to,
        timestamp = clock.millis(),
        deletedAt = null,
        createdAt = 1L,
        updatedAt = 1L,
        categoryId = categoryId,
        note = note,
    ).also { transactions.stored[ROW_ID] = it }

    @Test
    fun `edit mode fills the form from the row`() =
        runTest(mainRule.dispatcher) {
            seed()
            row(TransactionType.TRANSFER, amount = 200_000L, to = BANK, fee = 3_300L, note = "ATM")

            val s = openEdit().current

            assertTrue(s.isEditing)
            assertFalse(s.isLoading)
            assertEquals("200000", s.expression)
            assertEquals(AmountResult.Ok(200_000L), s.result)
            assertEquals(TransactionType.TRANSFER, s.type)
            assertEquals(CASH, s.selectedAccountId)
            assertEquals(BANK, s.destinationId)
            assertEquals(3_300L, s.fee)
            assertEquals("ATM", s.note)
            assertEquals(LocalDate.of(2026, 11, 4), s.date)
            assertTrue(s.isToday)
            assertEquals(ReimbursementChoice.NORMAL, s.reimbursement)
        }

    @Test
    fun `edit mode does not seed from the last choices`() =
        runTest(mainRule.dispatcher) {
            seed()
            transactions.lastAccountId = BANK
            transactions.lastCategoryIds[TransactionType.EXPENSE] = "c1"
            row()

            val s = openEdit().current

            assertEquals(CASH, s.selectedAccountId)
            assertNull(s.selectedCategoryIds[TransactionType.EXPENSE])
        }

    @Test
    fun `a row without a category stays without one`() =
        runTest(mainRule.dispatcher) {
            seed()
            transactions.stored[ROW_ID] = row().copy(source = TransactionSource.NOTIFICATION_PARSER)
            val viewModel = openEdit()
            assertNull(viewModel.current.selectedCategoryIds[TransactionType.EXPENSE])

            viewModel.onIntent(CashEntryIntent.SaveClicked)
            runCurrent()

            assertNull(transactions.stored.getValue(ROW_ID).categoryId)
        }

    @Test
    fun `a hidden category stays selected`() =
        runTest(mainRule.dispatcher) {
            seed()
            categories.setHidden("c1", hidden = true, updatedAt = 2L)
            row(categoryId = "c1")
            val viewModel = openEdit()

            assertEquals("c1", viewModel.current.selectedCategoryIds[TransactionType.EXPENSE])
            viewModel.onIntent(CashEntryIntent.NoteChanged("lunch"))
            viewModel.onIntent(CashEntryIntent.SaveClicked)
            runCurrent()

            assertEquals("c1", transactions.stored.getValue(ROW_ID).categoryId)
        }

    @Test
    fun `a hidden category is offered only to its own transaction`() =
        runTest(mainRule.dispatcher) {
            seed()
            categories.setHidden("c1", hidden = true, updatedAt = 2L)
            row(categoryId = "c1")

            assertEquals(listOf("c1", "c2"), openEdit().current.categories.map { it.id })
            assertEquals(listOf("c2"), open(transactionId = null).current.categories.map { it.id })
        }

    @Test
    fun `a hidden category stays when the lists change later`() =
        runTest(mainRule.dispatcher) {
            seed()
            row(categoryId = "c1")
            val viewModel = openEdit()

            categories.setHidden("c1", hidden = true, updatedAt = 2L)
            runCurrent()

            assertEquals("c1", viewModel.current.selectedCategoryIds[TransactionType.EXPENSE])
            assertEquals(listOf("c1", "c2"), viewModel.current.categories.map { it.id })
        }

    @Test
    fun `a hidden category is not offered on the other type`() =
        runTest(mainRule.dispatcher) {
            seed()
            categories.setHidden("c1", hidden = true, updatedAt = 2L)
            row(categoryId = "c1")
            val viewModel = openEdit()

            viewModel.onIntent(CashEntryIntent.TypeChanged(TransactionType.INCOME))

            assertEquals(listOf("i1"), viewModel.current.categories.map { it.id })
        }

    @Test
    fun `a deleted category of the row shows as none`() =
        runTest(mainRule.dispatcher) {
            seed()
            categories.stored["c1"] = testCategory("c1").copy(deletedAt = 2L)
            row(categoryId = "c1")

            assertNull(openEdit().current.selectedCategoryIds[TransactionType.EXPENSE])
        }

    @Test
    fun `save in edit mode updates and does not add`() =
        runTest(mainRule.dispatcher) {
            seed()
            row(categoryId = "c1", note = "lunch")
            val viewModel = openEdit()
            viewModel.press(CalcKey.BACKSPACE, CalcKey.D1)

            viewModel.onIntent(CashEntryIntent.SaveClicked)
            runCurrent()

            val saved = transactions.stored.values.single()
            assertEquals(50_001L, saved.amount)
            assertEquals(ROW_ID, saved.id)
            assertEquals("c1", saved.categoryId)
            assertEquals(1, transactions.writes)
            assertFalse(viewModel.current.isVisible)
        }

    @Test
    fun `transfer changed to expense saves no destination and no fee`() =
        runTest(mainRule.dispatcher) {
            seed()
            row(TransactionType.TRANSFER, amount = 200_000L, to = BANK, fee = 3_300L)
            val viewModel = openEdit()
            viewModel.onIntent(CashEntryIntent.TypeChanged(TransactionType.EXPENSE))

            viewModel.onIntent(CashEntryIntent.SaveClicked)
            runCurrent()

            val saved = transactions.stored.getValue(ROW_ID)
            assertEquals(TransactionType.EXPENSE, saved.type)
            assertNull(saved.toAccountId)
            assertEquals(0L, saved.feeAmount)
            assertNull(saved.categoryId)
        }

    @Test
    fun `reimbursement pick is saved for an expense`() =
        runTest(mainRule.dispatcher) {
            seed()
            row()
            val viewModel = openEdit()

            viewModel.onIntent(CashEntryIntent.ReimbursementPicked(ReimbursementChoice.PENDING))
            viewModel.onIntent(CashEntryIntent.SaveClicked)
            runCurrent()

            val saved = transactions.stored.getValue(ROW_ID)
            assertTrue(saved.isExcludedFromAnalytics)
            assertEquals(ReimbursementStatus.PENDING, saved.reimbursementStatus)
        }

    @Test
    fun `delete confirmed soft-deletes and closes`() =
        runTest(mainRule.dispatcher) {
            seed()
            row()
            val viewModel = openEdit()

            viewModel.onIntent(CashEntryIntent.DeleteClicked)
            assertTrue(viewModel.current.isDeleteConfirmOpen)
            viewModel.onIntent(CashEntryIntent.DeleteDismissed)
            assertFalse(viewModel.current.isDeleteConfirmOpen)
            viewModel.onIntent(CashEntryIntent.DeleteClicked)
            viewModel.onIntent(CashEntryIntent.DeleteConfirmed)
            runCurrent()

            assertNotNull(transactions.stored.getValue(ROW_ID).deletedAt)
            assertFalse(viewModel.current.isDeleteConfirmOpen)
            assertFalse(viewModel.current.isVisible)
        }

    @Test
    fun `a failed delete closes the dialog and shows the save error`() =
        runTest(mainRule.dispatcher) {
            seed()
            row()
            val viewModel = openEdit()
            viewModel.onIntent(CashEntryIntent.DeleteClicked)
            transactions.stored.remove(ROW_ID)

            viewModel.onIntent(CashEntryIntent.DeleteConfirmed)
            runCurrent()

            assertFalse(viewModel.current.isDeleteConfirmOpen)
            assertEquals(CashEntrySaveError.GENERIC, viewModel.current.saveError)
            assertFalse(viewModel.current.isSaving)
            assertTrue(viewModel.current.isVisible)
        }

    @Test
    fun `a storage failure on delete shows the save error`() =
        runTest(mainRule.dispatcher) {
            seed()
            row()
            val viewModel = openEdit()
            transactions.failWrite = StorageException(RuntimeException("disk"))

            viewModel.onIntent(CashEntryIntent.DeleteConfirmed)
            runCurrent()

            assertEquals(CashEntrySaveError.GENERIC, viewModel.current.saveError)
            assertFalse(viewModel.current.isSaving)
            assertTrue(viewModel.current.isVisible)
        }

    @Test
    fun `delete is ignored in create mode`() =
        runTest(mainRule.dispatcher) {
            seed()
            row()
            val viewModel = open(transactionId = null)

            viewModel.onIntent(CashEntryIntent.DeleteClicked)
            viewModel.onIntent(CashEntryIntent.DeleteConfirmed)
            runCurrent()

            assertFalse(viewModel.current.isDeleteConfirmOpen)
            assertFalse(viewModel.current.isSaving)
            assertNull(transactions.stored.getValue(ROW_ID).deletedAt)
        }

    /** Stores a soft-deleted original that an auto-pair folded into [ROW_ID]. */
    private fun original(
        id: String,
        type: TransactionType,
        account: String,
    ) {
        transactions.stored[id] =
            transactions.stored.getValue(ROW_ID).copy(
                id = id,
                type = type,
                accountId = account,
                toAccountId = null,
                deletedAt = 5L,
                pairedIntoId = ROW_ID,
            )
    }

    private fun paired(type: TransactionType = TransactionType.TRANSFER) {
        if (type == TransactionType.TRANSFER) row(type, to = BANK) else row(type)
        original("o1", TransactionType.EXPENSE, CASH)
        original("o2", TransactionType.INCOME, BANK)
    }

    @Test
    fun `a row with paired originals opens as auto-paired`() =
        runTest(mainRule.dispatcher) {
            seed()
            paired()

            assertTrue(openEdit().current.isAutoPaired)
        }

    @Test
    fun `a paired row switched to expense is still auto-paired`() =
        runTest(mainRule.dispatcher) {
            seed()
            paired()
            val viewModel = openEdit()

            viewModel.onIntent(CashEntryIntent.TypeChanged(TransactionType.EXPENSE))

            assertTrue(viewModel.current.isAutoPaired)
        }

    @Test
    fun `a paired row stored as expense opens as auto-paired`() =
        runTest(mainRule.dispatcher) {
            seed()
            paired(TransactionType.EXPENSE)

            assertTrue(openEdit().current.isAutoPaired)
        }

    @Test
    fun `a manual transfer is not auto-paired`() =
        runTest(mainRule.dispatcher) {
            seed()
            row(TransactionType.TRANSFER, to = BANK)

            assertFalse(openEdit().current.isAutoPaired)
        }

    @Test
    fun `unpair confirmed splits the transfer once and hides the sheet`() =
        runTest(mainRule.dispatcher) {
            seed()
            paired()
            val viewModel = openEdit()

            viewModel.onIntent(CashEntryIntent.UnpairClicked)
            assertTrue(viewModel.current.isUnpairConfirmOpen)
            viewModel.onIntent(CashEntryIntent.UnpairDismissed)
            assertFalse(viewModel.current.isUnpairConfirmOpen)
            viewModel.onIntent(CashEntryIntent.UnpairClicked)
            viewModel.onIntent(CashEntryIntent.UnpairConfirmed)
            runCurrent()

            assertEquals(1, pairs.unpairs)
            assertNotNull(transactions.stored.getValue(ROW_ID).deletedAt)
            assertNull(transactions.stored.getValue("o1").deletedAt)
            assertFalse(viewModel.current.isUnpairConfirmOpen)
            assertFalse(viewModel.current.isVisible)
        }

    @Test
    fun `a failed unpair closes the dialog and shows the save error`() =
        runTest(mainRule.dispatcher) {
            seed()
            row(TransactionType.TRANSFER, to = BANK)
            val viewModel = openEdit()
            viewModel.onIntent(CashEntryIntent.UnpairClicked)

            viewModel.onIntent(CashEntryIntent.UnpairConfirmed)
            runCurrent()

            assertEquals(0, pairs.unpairs)
            assertFalse(viewModel.current.isUnpairConfirmOpen)
            assertEquals(CashEntrySaveError.GENERIC, viewModel.current.saveError)
            assertFalse(viewModel.current.isSaving)
            assertTrue(viewModel.current.isVisible)
        }

    @Test
    fun `unpair refused for an archived account says so`() =
        runTest(mainRule.dispatcher) {
            seed()
            paired()
            original("o3", TransactionType.INCOME, "archived")
            val viewModel = openEdit()
            viewModel.onIntent(CashEntryIntent.UnpairClicked)

            viewModel.onIntent(CashEntryIntent.UnpairConfirmed)
            runCurrent()

            assertEquals(0, pairs.unpairs)
            assertEquals(CashEntrySaveError.ACCOUNT_UNUSABLE, viewModel.current.saveError)
            assertFalse(viewModel.current.isSaving)
            assertTrue(viewModel.current.isVisible)
        }

    @Test
    fun `unpair is ignored while saving and in create mode`() =
        runTest(mainRule.dispatcher) {
            seed()
            paired()
            val saving = openEdit()
            saving.onIntent(CashEntryIntent.SaveClicked)

            saving.onIntent(CashEntryIntent.UnpairClicked)
            saving.onIntent(CashEntryIntent.UnpairConfirmed)
            runCurrent()

            assertFalse(saving.current.isUnpairConfirmOpen)
            assertEquals(0, pairs.unpairs)

            val create = open(transactionId = null)
            create.onIntent(CashEntryIntent.UnpairClicked)
            create.onIntent(CashEntryIntent.UnpairConfirmed)
            runCurrent()

            assertFalse(create.current.isUnpairConfirmOpen)
            assertEquals(0, pairs.unpairs)
        }

    @Test
    fun `a missing row sends Close`() =
        runTest(mainRule.dispatcher) {
            seed()
            val viewModel = openEdit()

            viewModel.effects.test {
                assertEquals(CashEntryEffect.Close, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
            assertTrue(viewModel.current.isLoading)
        }

    @Test
    fun `a storage failure while loading the row sends Close`() =
        runTest(mainRule.dispatcher) {
            seed()
            val failing =
                object : TransactionRepository by transactions {
                    override suspend fun getTransaction(id: String): Transaction? =
                        throw StorageException(RuntimeException("disk"))
                }
            val viewModel = openEdit(failing)

            viewModel.effects.test {
                assertEquals(CashEntryEffect.Close, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `a row on an archived account sends Close`() =
        runTest(mainRule.dispatcher) {
            seed()
            row()
            accounts.setArchived(CASH, archived = true, updatedAt = 2L)
            val viewModel = openEdit()

            viewModel.effects.test {
                assertEquals(CashEntryEffect.Close, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `an adjustment row sends Close`() =
        runTest(mainRule.dispatcher) {
            seed()
            transactions.stored[ROW_ID] =
                row().copy(type = TransactionType.ADJUSTMENT, adjustmentDirection = AdjustmentDirection.IN)
            val viewModel = openEdit()

            viewModel.effects.test {
                assertEquals(CashEntryEffect.Close, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `the source account leaving the list hides the sheet and picks nothing`() =
        runTest(mainRule.dispatcher) {
            seed()
            row()
            val viewModel = openEdit()

            accounts.setArchived(CASH, archived = true, updatedAt = 2L)
            runCurrent()

            assertFalse(viewModel.current.isVisible)
            assertEquals(CASH, viewModel.current.selectedAccountId)
        }

    @Test
    fun `the source account leaving the list after the row loaded sends Close once`() =
        runTest(mainRule.dispatcher) {
            seed()
            row()
            val viewModel = openEdit()

            viewModel.effects.test {
                accounts.setArchived(CASH, archived = true, updatedAt = 2L)
                runCurrent()
                assertEquals(CashEntryEffect.Close, awaitItem())

                accounts.setArchived(BANK, archived = true, updatedAt = 3L)
                runCurrent()
                expectNoEvents()
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `a later lists emission does not overwrite what the user typed`() =
        runTest(mainRule.dispatcher) {
            seed()
            row(note = "old")
            val viewModel = openEdit()
            viewModel.onIntent(CashEntryIntent.NoteChanged("new"))
            viewModel.press(CalcKey.D9)

            accounts.setArchived(BANK, archived = true, updatedAt = 2L)
            runCurrent()

            assertEquals("new", viewModel.current.note)
            assertEquals("500009", viewModel.current.expression)
        }

    @Test
    fun `a second view model for the same id fills the form again`() =
        runTest(mainRule.dispatcher) {
            seed()
            row(amount = 70_000L)
            open()

            val second = open()

            assertEquals("70000", second.current.expression)
            assertTrue(second.current.isEditing)
            assertFalse(second.current.isLoading)
        }

    @Test
    fun `a destination that is archived shows as none and blocks save`() =
        runTest(mainRule.dispatcher) {
            seed()
            val original = row(TransactionType.TRANSFER, amount = 200_000L, to = BANK)
            accounts.setArchived(BANK, archived = true, updatedAt = 2L)
            val viewModel = openEdit()

            assertNull(viewModel.current.destinationId)
            assertFalse(viewModel.current.isSaveEnabled)
            viewModel.onIntent(CashEntryIntent.SaveClicked)
            runCurrent()

            assertEquals(original, transactions.stored.getValue(ROW_ID))
        }

    @Test
    fun `a fourteen digit amount survives an edit of the note only`() =
        runTest(mainRule.dispatcher) {
            seed()
            row(amount = 99_999_999_999_999L)
            val viewModel = openEdit()
            viewModel.onIntent(CashEntryIntent.NoteChanged("n"))

            viewModel.onIntent(CashEntryIntent.SaveClicked)
            runCurrent()

            // The note proves the save went through; an unchanged amount alone would also pass on a refused save.
            assertEquals("n", transactions.stored.getValue(ROW_ID).note)
            assertEquals(99_999_999_999_999L, transactions.stored.getValue(ROW_ID).amount)
        }

    private companion object {
        const val CASH = "sys_cash"
        const val BANK = "acc-1"
        const val ROW_ID = "t1"
    }
}
