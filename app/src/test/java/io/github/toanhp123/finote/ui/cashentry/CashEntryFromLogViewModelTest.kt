package io.github.toanhp123.finote.ui.cashentry

import app.cash.turbine.test
import io.github.toanhp123.finote.domain.account.model.AccountIdentifier
import io.github.toanhp123.finote.domain.account.model.IdentifierKind
import io.github.toanhp123.finote.domain.account.testAccount
import io.github.toanhp123.finote.domain.account.usecase.FakeAccountRepository
import io.github.toanhp123.finote.domain.category.usecase.FakeCategoryRepository
import io.github.toanhp123.finote.domain.notification.model.AdjustmentToken
import io.github.toanhp123.finote.domain.notification.model.BookInboxResult
import io.github.toanhp123.finote.domain.notification.model.FailedInboxItem
import io.github.toanhp123.finote.domain.notification.model.InboxBooking
import io.github.toanhp123.finote.domain.notification.model.ParsedFields
import io.github.toanhp123.finote.domain.notification.repository.FailedInboxRepository
import io.github.toanhp123.finote.domain.transaction.error.TransactionError
import io.github.toanhp123.finote.domain.transaction.model.TransactionType
import io.github.toanhp123.finote.domain.transaction.usecase.FakeTransactionRepository
import io.github.toanhp123.finote.ui.cashentry.logic.AmountResult
import io.github.toanhp123.finote.ui.common.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
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

private const val PKG = "fake.bank"
private const val CASH_ID = "sys_cash"
private const val BANK_ID = "acc_bank"
private const val ITEM_ID = "log-2"
private const val POST_TIME = "2026-11-03T01:30:00Z" // 08:30 in Ho Chi Minh City

/** Answers the reads of one item and records every booking and dismiss; the real rules are tested in the data layer. */
private class InboxFake : FailedInboxRepository {
    var item: FailedInboxItem? = null
    val identifiers = mutableListOf<AccountIdentifier>()
    val bookAnswers = ArrayDeque<BookInboxResult>()
    val bookCalls = mutableListOf<Pair<InboxBooking, AdjustmentToken?>>()
    val dismissCalls = mutableListOf<Pair<String, List<String>>>()
    var dismissResult = true

    override fun observeInbox(): Flow<List<FailedInboxItem>> = emptyFlow()

    override suspend fun getItem(id: String): FailedInboxItem? = item?.takeIf { it.id == id }

    override suspend fun getIdentifiers(packageName: String): List<AccountIdentifier> = identifiers

    override suspend fun book(
        booking: InboxBooking,
        confirmation: AdjustmentToken?,
        transactionId: String,
        now: Long,
    ): BookInboxResult {
        bookCalls += booking to confirmation
        return bookAnswers.removeFirst()
    }

    override suspend fun dismiss(
        itemId: String,
        logIds: List<String>,
    ): Boolean {
        dismissCalls += itemId to logIds
        return dismissResult
    }
}

private fun inboxItem(
    mask: String = "1234",
    reason: String = "settle_error",
) = FailedInboxItem(
    id = ITEM_ID,
    logIds = listOf("log-1", ITEM_ID),
    packageName = PKG,
    title = "Card spend",
    content = "A made-up text",
    postTime = Instant.parse(POST_TIME).toEpochMilli(),
    receivedAt = Instant.parse(POST_TIME).toEpochMilli(),
    reason = reason,
    parsed = ParsedFields(150_000L, TransactionType.EXPENSE, mask, null, "Cafe", null),
    isHidden = false,
    isKeywordHidden = false,
)

/** The from-log mode of the cash entry sheet: prefill, the exact time, and how each booking result shows. */
@OptIn(ExperimentalCoroutinesApi::class)
class CashEntryFromLogViewModelTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    private val clock = Clock.fixed(Instant.parse("2026-11-04T03:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"))
    private val accounts = FakeAccountRepository()
    private val transactions = FakeTransactionRepository(activeAccounts = setOf(CASH_ID, BANK_ID))
    private val inbox = InboxFake()
    private val tokenA = AdjustmentToken(CASH_ID, 1L, 2L)
    private val tokenB = AdjustmentToken(BANK_ID, 1L, 2L)

    private fun TestScope.open(item: FailedInboxItem? = inboxItem()): CashEntryViewModel {
        accounts.stored[CASH_ID] = testAccount(CASH_ID).copy(bankPackageName = PKG)
        accounts.stored[BANK_ID] = testAccount(BANK_ID).copy(sortOrder = 1)
        inbox.identifiers += AccountIdentifier(PKG, IdentifierKind.ACCOUNT, "1234", CASH_ID)
        inbox.item = item
        return fromLogViewModel(ITEM_ID, inbox, accounts, FakeCategoryRepository(), transactions, clock)
            .also { runCurrent() }
    }

    private val CashEntryViewModel.current get() = state.value

    private fun CashEntryViewModel.send(intent: CashEntryIntent) {
        onIntent(intent)
        mainRule.dispatcher.scheduler.runCurrent()
    }

    private fun CashEntryViewModel.save() = send(CashEntryIntent.SaveClicked)

    @Test
    fun `from-log mode fills amount note and empty account`() =
        runTest(mainRule.dispatcher) {
            val s = open(inboxItem(mask = "9999")).current

            assertFalse(s.isLoading)
            assertFalse(s.isEditing)
            assertEquals(AmountResult.Ok(150_000L), s.result)
            assertEquals("150000", s.expression)
            assertEquals("Cafe", s.note)
            assertEquals(TransactionType.EXPENSE, s.type)
            assertNull(s.selectedAccountId)
            assertEquals(LocalDate.of(2026, 11, 3), s.date)
            assertEquals(SourceText("Card spend", "A made-up text"), s.sourceText)
            assertFalse(s.isDateMissing)
        }

    @Test
    fun `the owner of the card mask is preselected`() =
        runTest(mainRule.dispatcher) {
            assertEquals(CASH_ID, open().current.selectedAccountId)
        }

    @Test
    fun `save is disabled without an account`() =
        runTest(mainRule.dispatcher) {
            val vm = open(inboxItem(mask = "9999"))
            assertFalse(vm.current.isSaveEnabled)

            vm.send(CashEntryIntent.AccountPicked(BANK_ID))

            assertTrue(vm.current.isSaveEnabled)
        }

    @Test
    fun `untouched date keeps the post time`() =
        runTest(mainRule.dispatcher) {
            val vm = open()
            inbox.bookAnswers += BookInboxResult.Booked

            vm.save()

            val (booking, token) = inbox.bookCalls.single()
            assertEquals(Instant.parse(POST_TIME).toEpochMilli(), booking.timestamp)
            assertEquals(listOf("log-1", ITEM_ID), booking.logIds)
            assertEquals(CASH_ID, booking.resolvedAccountId)
            assertEquals(150_000L, booking.amount)
            assertNull(token)
            assertTrue(transactions.stored.isEmpty())
        }

    @Test
    fun `picked date keeps the time of day`() =
        runTest(mainRule.dispatcher) {
            val vm = open()
            inbox.bookAnswers += BookInboxResult.Booked

            vm.send(CashEntryIntent.DatePicked(LocalDate.of(2026, 11, 1)))
            vm.save()

            assertEquals(
                Instant.parse("2026-11-01T01:30:00Z").toEpochMilli(),
                inbox.bookCalls
                    .single()
                    .first.timestamp,
            )
        }

    @Test
    fun `a missing time needs a picked date and books at noon`() =
        runTest(mainRule.dispatcher) {
            val vm = open(inboxItem(reason = "invalid_time").copy(postTime = 0L))
            assertTrue(vm.current.isDateMissing)
            assertFalse(vm.current.isSaveEnabled)
            inbox.bookAnswers += BookInboxResult.Booked

            vm.send(CashEntryIntent.DatePicked(LocalDate.of(2026, 11, 1)))
            vm.save()

            assertEquals(
                Instant.parse("2026-11-01T05:00:00Z").toEpochMilli(),
                inbox.bookCalls
                    .single()
                    .first.timestamp,
            )
        }

    @Test
    fun `confirmation dialog then booked closes`() =
        runTest(mainRule.dispatcher) {
            val vm = open()
            inbox.bookAnswers += BookInboxResult.NeedsAdjustmentConfirmation(tokenA)
            inbox.bookAnswers += BookInboxResult.Booked

            vm.save()
            assertEquals(tokenA, vm.current.pendingConfirmation)
            assertFalse(vm.current.isSaving)
            assertTrue(vm.current.isVisible)

            vm.send(CashEntryIntent.AdjustmentConfirmed)

            assertEquals(listOf(null, tokenA), inbox.bookCalls.map { it.second })
            assertNull(vm.current.pendingConfirmation)
            assertFalse(vm.current.isVisible)
        }

    @Test
    fun `changing account after the dialog asks again`() =
        runTest(mainRule.dispatcher) {
            val vm = open()
            inbox.bookAnswers += BookInboxResult.NeedsAdjustmentConfirmation(tokenA)
            inbox.bookAnswers += BookInboxResult.NeedsAdjustmentConfirmation(tokenB)
            vm.save()

            vm.send(CashEntryIntent.AccountPicked(BANK_ID))
            vm.send(CashEntryIntent.AdjustmentConfirmed)

            val (booking, token) = inbox.bookCalls.last()
            assertEquals(BANK_ID, booking.accountId)
            assertEquals(tokenA, token)
            assertEquals(tokenB, vm.current.pendingConfirmation)
            assertTrue(vm.current.isVisible)
        }

    @Test
    fun `cancelling the dialog drops the token so the next save sends none`() =
        runTest(mainRule.dispatcher) {
            val vm = open()
            inbox.bookAnswers += BookInboxResult.NeedsAdjustmentConfirmation(tokenA)
            inbox.bookAnswers += BookInboxResult.NeedsAdjustmentConfirmation(tokenA)
            vm.save()

            vm.send(CashEntryIntent.AdjustmentDismissed)
            assertNull(vm.current.pendingConfirmation)
            vm.save()

            assertEquals(listOf(null, null), inbox.bookCalls.map { it.second })
            assertEquals(tokenA, vm.current.pendingConfirmation)
        }

    @Test
    fun `covered shows the notice and dismiss`() =
        runTest(mainRule.dispatcher) {
            val vm = open()
            inbox.bookAnswers += BookInboxResult.CoveredByAdjustment

            vm.save()
            assertTrue(vm.current.isCoveredNotice)
            assertTrue(vm.current.isVisible)
            assertFalse(vm.current.isSaving)

            vm.send(CashEntryIntent.DismissFromCovered)

            assertEquals(listOf(ITEM_ID to listOf("log-1", ITEM_ID)), inbox.dismissCalls)
            assertFalse(vm.current.isVisible)
        }

    @Test
    fun `already handled closes with a message`() =
        runTest(mainRule.dispatcher) {
            val vm = open()
            inbox.bookAnswers += BookInboxResult.AlreadyHandled

            vm.effects.test {
                vm.save()

                assertEquals(CashEntryEffect.ShowAlreadyHandled, awaitItem())
                assertFalse(vm.current.isVisible)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `an item that is gone closes with a message`() =
        runTest(mainRule.dispatcher) {
            val vm = open(item = null)

            vm.effects.test {
                assertEquals(CashEntryEffect.ShowAlreadyHandled, awaitItem())
                assertEquals(CashEntryEffect.Close, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
            assertFalse(vm.current.isVisible)
        }

    @Test
    fun `a refused booking shows the save error`() =
        runTest(mainRule.dispatcher) {
            val vm = open()
            inbox.bookAnswers += BookInboxResult.Refused(TransactionError.InvalidAmount)

            vm.save()

            assertEquals(CashEntrySaveError.GENERIC, vm.current.saveError)
            assertFalse(vm.current.isSaving)
            assertTrue(vm.current.isVisible)
        }
}
