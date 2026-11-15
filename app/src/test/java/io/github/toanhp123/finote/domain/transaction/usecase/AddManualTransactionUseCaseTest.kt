package io.github.toanhp123.finote.domain.transaction.usecase

import io.github.toanhp123.finote.domain.common.FakeTransactionRunner
import io.github.toanhp123.finote.domain.common.Outcome
import io.github.toanhp123.finote.domain.transaction.error.TransactionError
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

class AddManualTransactionUseCaseTest {
    private val clock = Clock.fixed(Instant.parse("2026-11-10T08:30:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"))
    private val repo = FakeTransactionRepository(activeAccounts = setOf(ACCOUNT, OTHER))
    private val addManual =
        AddManualTransactionUseCase(AddTransactionUseCase(repo, FakeTransactionRunner(), clock), clock)

    private suspend fun add(
        date: LocalDate = LocalDate.of(2026, 11, 10),
        note: String = "",
    ) = addManual(50_000L, TransactionType.EXPENSE, ACCOUNT, null, date, note)

    @Test
    fun `today keeps the clock time`() =
        runTest {
            add()

            assertEquals(
                clock.millis(),
                repo.stored.values
                    .single()
                    .timestamp,
            )
        }

    @Test
    fun `another day is local noon`() =
        runTest {
            add(date = LocalDate.of(2026, 11, 9))

            val expected = Instant.parse("2026-11-09T12:00:00+07:00").toEpochMilli()
            assertEquals(
                expected,
                repo.stored.values
                    .single()
                    .timestamp,
            )
        }

    @Test
    fun `note is trimmed, blank stays empty`() =
        runTest {
            add(note = "  lunch ")
            add(note = "   ")

            assertEquals(
                listOf("lunch", ""),
                repo.stored.values
                    .map { it.note }
                    .sortedDescending(),
            )
        }

    @Test
    fun `source is MANUAL`() =
        runTest {
            add()

            val saved = repo.stored.values.single()
            assertEquals(TransactionSource.MANUAL, saved.source)
            assertEquals(0L, saved.feeAmount)
            assertNull(saved.toAccountId)
            assertNull(saved.adjustmentDirection)
            assertEquals(50_000L, saved.amount)
            assertEquals(ACCOUNT, saved.accountId)
        }

    @Test
    fun `an unusable account is returned as AccountUnusable and nothing is stored`() =
        runTest {
            assertEquals(
                Outcome.Err(TransactionError.AccountUnusable),
                addManual(50_000L, TransactionType.EXPENSE, "ghost", null, LocalDate.of(2026, 11, 10), ""),
            )
            assertEquals(0, repo.writes)
        }

    private suspend fun transfer(
        amount: Long = 50_000L,
        to: String? = OTHER,
        fee: Long = 1_000L,
        categoryId: String? = null,
    ) = addManual(amount, TransactionType.TRANSFER, ACCOUNT, categoryId, LocalDate.of(2026, 11, 10), "", to, fee)

    @Test
    fun `a transfer stores destination and fee with no category`() =
        runTest {
            assertEquals(Outcome.Ok(Unit), transfer())

            val saved = repo.stored.values.single()
            assertEquals(TransactionType.TRANSFER, saved.type)
            assertEquals(ACCOUNT, saved.accountId)
            assertEquals(OTHER, saved.toAccountId)
            assertEquals(1_000L, saved.feeAmount)
            assertNull(saved.categoryId)
        }

    @Test
    fun `bad transfer combinations are refused and nothing is stored`() =
        runTest {
            val invalid = Outcome.Err(TransactionError.InvalidTransfer)
            assertEquals(invalid, transfer(to = null))
            assertEquals(invalid, transfer(to = ACCOUNT))
            assertEquals(invalid, transfer(categoryId = "c1"))
            assertEquals(invalid, transfer(fee = -1L))
            assertEquals(
                invalid,
                addManual(5L, TransactionType.EXPENSE, ACCOUNT, null, LocalDate.of(2026, 11, 10), "", OTHER),
            )
            assertEquals(
                invalid,
                addManual(5L, TransactionType.INCOME, ACCOUNT, null, LocalDate.of(2026, 11, 10), "", null, 1L),
            )
            assertEquals(0, repo.writes)
        }

    @Test
    fun `a transfer whose amount plus fee overflows is refused`() =
        runTest {
            assertEquals(Outcome.Err(TransactionError.AmountOverflow), transfer(amount = Long.MAX_VALUE, fee = 1L))
            assertEquals(Outcome.Ok(Unit), transfer(amount = Long.MAX_VALUE - 1L, fee = 1L))
        }

    private companion object {
        const val ACCOUNT = "acc"
        const val OTHER = "acc-2"
    }
}
