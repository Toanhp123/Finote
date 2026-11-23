package io.github.toanhp123.finote.domain.transaction.usecase

import io.github.toanhp123.finote.domain.account.A
import io.github.toanhp123.finote.domain.account.B
import io.github.toanhp123.finote.domain.account.C
import io.github.toanhp123.finote.domain.account.adjustment
import io.github.toanhp123.finote.domain.account.tx
import io.github.toanhp123.finote.domain.common.FakeTransactionRunner
import io.github.toanhp123.finote.domain.common.Outcome
import io.github.toanhp123.finote.domain.transaction.error.TransactionError
import io.github.toanhp123.finote.domain.transaction.model.AdjustmentDirection
import io.github.toanhp123.finote.domain.transaction.model.ReimbursementStatus
import io.github.toanhp123.finote.domain.transaction.model.TransactionEdit
import io.github.toanhp123.finote.domain.transaction.model.TransactionFields
import io.github.toanhp123.finote.domain.transaction.model.TransactionSource
import io.github.toanhp123.finote.domain.transaction.model.TransactionType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class UpdateTransactionUseCaseTest {
    private val zone = ZoneId.of("Asia/Ho_Chi_Minh")
    private val clock = Clock.fixed(Instant.parse("2026-11-15T07:00:00Z"), zone)
    private val repo = FakeTransactionRepository(activeAccounts = setOf(A, B))
    private val runner = FakeTransactionRunner()
    private val update = UpdateTransactionUseCase(repo, runner, clock)

    // 23:30 local on 14/11.
    private val stored =
        tx(TransactionType.EXPENSE, 150_000, at = Instant.parse("2026-11-14T16:30:00Z").toEpochMilli()).copy(
            createdAt = 1L,
            updatedAt = 1L,
            postBalance = 5_000_000L,
            source = TransactionSource.NOTIFICATION_PARSER,
            notificationLogId = "log1",
            merchantName = "CIRCLE K",
        )
    private val oldDate = LocalDate.of(2026, 11, 14)

    private fun fieldsOf(
        amount: Long = 150_000,
        fee: Long = 0,
        type: TransactionType = TransactionType.EXPENSE,
        account: String = A,
        to: String? = null,
    ) = TransactionFields(amount, fee, type, account, to, null)

    private fun editOf(
        fields: TransactionFields = fieldsOf(),
        date: LocalDate = oldDate,
        note: String = "",
        excluded: Boolean = false,
        status: ReimbursementStatus = ReimbursementStatus.NONE,
    ) = TransactionEdit(fields, date, note, excluded, status)

    private fun saved() = repo.stored.getValue("tx")

    @Test
    fun `note-only edit keeps the notification fields and stamps updatedAt`() =
        runTest {
            repo.stored["tx"] = stored
            assertEquals(Outcome.Ok(Unit), update("tx", editOf(note = "  lunch  ")))
            val row = saved()
            assertEquals("lunch", row.note)
            assertEquals(TransactionSource.NOTIFICATION_PARSER, row.source)
            assertEquals("log1", row.notificationLogId)
            assertEquals("CIRCLE K", row.merchantName)
            assertEquals(1L, row.createdAt)
            assertEquals(stored.timestamp, row.timestamp)
            assertEquals(5_000_000L, row.postBalance)
            assertEquals(clock.millis(), row.updatedAt)
            assertEquals(1, runner.writes)
        }

    @Test
    fun `amount change on the same date and account keeps postBalance`() =
        runTest {
            repo.stored["tx"] = stored
            assertEquals(Outcome.Ok(Unit), update("tx", editOf(fieldsOf(amount = 120_000))))
            assertEquals(120_000L, saved().amount)
            assertEquals(5_000_000L, saved().postBalance)
        }

    @Test
    fun `account change drops postBalance`() =
        runTest {
            repo.stored["tx"] = stored
            assertEquals(Outcome.Ok(Unit), update("tx", editOf(fieldsOf(account = B))))
            assertEquals(B, saved().accountId)
            assertNull(saved().postBalance)
        }

    @Test
    fun `date change drops postBalance and keeps the local time`() =
        runTest {
            repo.stored["tx"] = stored
            val date = LocalDate.of(2026, 11, 10)
            assertEquals(Outcome.Ok(Unit), update("tx", editOf(date = date)))
            assertNull(saved().postBalance)
            assertEquals(Instant.parse("2026-11-10T16:30:00Z").toEpochMilli(), saved().timestamp)
        }

    @Test
    fun `moving to today at a time that is still ahead uses now`() =
        runTest {
            repo.stored["tx"] = stored
            assertEquals(Outcome.Ok(Unit), update("tx", editOf(date = LocalDate.of(2026, 11, 15))))
            assertEquals(clock.millis(), saved().timestamp)
        }

    @Test
    fun `moving to today at a time that is already past keeps that time`() =
        runTest {
            // 08:00 local on 14/11; the fixed clock reads 14:00 local on 15/11.
            repo.stored["tx"] = stored.copy(timestamp = Instant.parse("2026-11-14T01:00:00Z").toEpochMilli())
            assertEquals(Outcome.Ok(Unit), update("tx", editOf(date = LocalDate.of(2026, 11, 15))))
            assertEquals(Instant.parse("2026-11-15T01:00:00Z").toEpochMilli(), saved().timestamp)
        }

    @Test
    fun `an ADJUSTMENT type in the fields is UnsupportedType and nothing is written`() =
        runTest {
            repo.stored["tx"] = stored
            val result = update("tx", editOf(fieldsOf(type = TransactionType.ADJUSTMENT)))
            assertEquals(Outcome.Err(TransactionError.UnsupportedType), result)
            assertEquals(0, repo.writes)
            assertEquals(stored, saved())
        }

    @Test
    fun `leaving EXPENSE resets the reimbursement pair`() =
        runTest {
            repo.stored["tx"] = stored
            val income = fieldsOf(type = TransactionType.INCOME)
            val edit = editOf(income, excluded = true, status = ReimbursementStatus.PENDING)
            assertEquals(Outcome.Ok(Unit), update("tx", edit))
            assertEquals(ReimbursementStatus.NONE, saved().reimbursementStatus)
            assertEquals(false, saved().isExcludedFromAnalytics)
        }

    @Test
    fun `an expense keeps the reimbursement pair`() =
        runTest {
            repo.stored["tx"] = stored
            assertEquals(
                Outcome.Ok(Unit),
                update("tx", editOf(excluded = true, status = ReimbursementStatus.PENDING)),
            )
            assertEquals(ReimbursementStatus.PENDING, saved().reimbursementStatus)
            assertEquals(true, saved().isExcludedFromAnalytics)
        }

    @Test
    fun `expense to transfer stores the destination and the fee and no category`() =
        runTest {
            repo.stored["tx"] = stored.copy(categoryId = "food")
            val edit = editOf(fieldsOf(fee = 1_100, type = TransactionType.TRANSFER, to = B))
            assertEquals(Outcome.Ok(Unit), update("tx", edit))
            assertEquals(B, saved().toAccountId)
            assertEquals(1_100L, saved().feeAmount)
            assertNull(saved().categoryId)
        }

    private val storedTransfer =
        stored.copy(
            type = TransactionType.TRANSFER,
            toAccountId = B,
            toPostBalance = 9_000_000L,
            toTimestamp = stored.timestamp + 1_000,
        )

    private fun transferEdit(to: String = B) = fieldsOf(type = TransactionType.TRANSFER, to = to)

    @Test
    fun `edit keeps receiver balance when date and both accounts stay`() =
        runTest {
            repo.stored["tx"] = storedTransfer
            assertEquals(Outcome.Ok(Unit), update("tx", editOf(transferEdit(), note = "x")))
            assertEquals(9_000_000L, saved().toPostBalance)
            assertEquals(storedTransfer.toTimestamp, saved().toTimestamp)
        }

    @Test
    fun `edit clears receiver balance when destination changes`() =
        runTest {
            val abc = FakeTransactionRepository(activeAccounts = setOf(A, B, C))
            abc.stored["tx"] = storedTransfer
            val result = UpdateTransactionUseCase(abc, runner, clock)("tx", editOf(transferEdit(to = C)))
            assertEquals(Outcome.Ok(Unit), result)
            assertNull(abc.stored.getValue("tx").toPostBalance)
            assertNull(abc.stored.getValue("tx").toTimestamp)
        }

    @Test
    fun `edit clears receiver balance when the date changes`() =
        runTest {
            repo.stored["tx"] = storedTransfer
            assertEquals(Outcome.Ok(Unit), update("tx", editOf(transferEdit(), date = LocalDate.of(2026, 11, 10))))
            assertNull(saved().toPostBalance)
            assertNull(saved().toTimestamp)
        }

    @Test
    fun `edit clears receiver balance when the type changes to expense`() =
        runTest {
            repo.stored["tx"] = storedTransfer
            assertEquals(Outcome.Ok(Unit), update("tx", editOf(fieldsOf())))
            assertNull(saved().toPostBalance)
            assertNull(saved().toTimestamp)
        }

    @Test
    fun `edit with a clock that has not moved still marks the row touched`() =
        runTest {
            repo.stored["tx"] = stored.copy(createdAt = clock.millis(), updatedAt = clock.millis())
            assertEquals(Outcome.Ok(Unit), update("tx", editOf(note = "x")))
            assertEquals(clock.millis() + 1, saved().updatedAt)
        }

    @Test
    fun `missing id is NotFound`() =
        runTest {
            assertEquals(Outcome.Err(TransactionError.NotFound), update("tx", editOf()))
            assertEquals(0, repo.writes)
        }

    @Test
    fun `deleted row is NotFound`() =
        runTest {
            repo.stored["tx"] = stored.copy(deletedAt = 1L)
            assertEquals(Outcome.Err(TransactionError.NotFound), update("tx", editOf()))
            assertEquals(0, repo.writes)
        }

    @Test
    fun `adjustment row is NotFound`() =
        runTest {
            repo.stored["tx"] = adjustment(AdjustmentDirection.IN, 1_000, at = stored.timestamp)
            assertEquals(Outcome.Err(TransactionError.NotFound), update("tx", editOf()))
            assertEquals(0, repo.writes)
        }

    @Test
    fun `zero amount is InvalidAmount and nothing is written`() =
        runTest {
            repo.stored["tx"] = stored
            assertEquals(Outcome.Err(TransactionError.InvalidAmount), update("tx", editOf(fieldsOf(amount = 0))))
            assertEquals(0, repo.writes)
        }

    @Test
    fun `old account archived is AccountUnusable`() =
        runTest {
            val onlyB = FakeTransactionRepository(activeAccounts = setOf(B))
            onlyB.stored["tx"] = stored
            val result = UpdateTransactionUseCase(onlyB, runner, clock)("tx", editOf(fieldsOf(account = B)))
            assertEquals(Outcome.Err(TransactionError.AccountUnusable), result)
            assertEquals(0, onlyB.writes)
        }

    @Test
    fun `new account archived is AccountUnusable`() =
        runTest {
            val onlyA = FakeTransactionRepository(activeAccounts = setOf(A))
            onlyA.stored["tx"] = stored
            val result = UpdateTransactionUseCase(onlyA, runner, clock)("tx", editOf(fieldsOf(account = B)))
            assertEquals(Outcome.Err(TransactionError.AccountUnusable), result)
            assertEquals(0, onlyA.writes)
        }

    @Test
    fun `balance overflow is AmountOverflow and the row stays`() =
        runTest {
            repo.stored["tx"] = stored
            repo.failWrite = ArithmeticException("long overflow")
            val result = update("tx", editOf(fieldsOf(amount = Long.MAX_VALUE)))
            assertEquals(Outcome.Err(TransactionError.AmountOverflow), result)
            assertEquals(150_000L, saved().amount)
        }

    @Test
    fun `a local time that does not exist on the new date still saves on that date`() =
        runTest {
            val berlin = ZoneId.of("Europe/Berlin")
            val berlinClock = Clock.fixed(Instant.parse("2026-11-15T07:00:00Z"), berlin)
            val at = Instant.parse("2026-03-28T01:30:00Z").toEpochMilli() // 02:30 CET
            repo.stored["tx"] = stored.copy(timestamp = at)
            val date = LocalDate.of(2026, 3, 29) // 02:30 is skipped by the clock change
            val result = UpdateTransactionUseCase(repo, runner, berlinClock)("tx", editOf(date = date))
            assertEquals(Outcome.Ok(Unit), result)
            assertEquals(date, Instant.ofEpochMilli(saved().timestamp).atZone(berlin).toLocalDate())
        }
}
