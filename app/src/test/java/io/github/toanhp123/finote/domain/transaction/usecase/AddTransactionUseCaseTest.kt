package io.github.toanhp123.finote.domain.transaction.usecase

import io.github.toanhp123.finote.domain.account.A
import io.github.toanhp123.finote.domain.account.B
import io.github.toanhp123.finote.domain.account.tx
import io.github.toanhp123.finote.domain.category.model.CategoryType
import io.github.toanhp123.finote.domain.common.FakeTransactionRunner
import io.github.toanhp123.finote.domain.common.Outcome
import io.github.toanhp123.finote.domain.transaction.error.TransactionError
import io.github.toanhp123.finote.domain.transaction.model.Transaction
import io.github.toanhp123.finote.domain.transaction.model.TransactionType
import io.github.toanhp123.finote.domain.transaction.service.TransactionRules
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

class AddTransactionUseCaseTest {
    private val now = Instant.parse("2026-11-03T00:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val repo =
        FakeTransactionRepository(
            activeAccounts = setOf(A, B),
            categories = mapOf("food" to CategoryType.EXPENSE, "salary" to CategoryType.INCOME),
        )
    private val runner = FakeTransactionRunner()
    private val add = AddTransactionUseCase(repo, runner, clock)

    private fun valid(
        type: TransactionType = TransactionType.EXPENSE,
        at: Instant = now,
    ) = tx(type, 100_000, at = at.toEpochMilli())

    private val limit = now.plus(TransactionRules.MAX_FUTURE_DAYS, ChronoUnit.DAYS)

    private suspend fun assertRejected(
        error: TransactionError,
        transaction: Transaction,
    ) {
        val writesBefore = repo.writes
        assertEquals(Outcome.Err(error), add(transaction))
        assertEquals(writesBefore, repo.writes)
    }

    @Test
    fun `valid transaction is stored with timestamps from the clock`() =
        runTest {
            assertEquals(Outcome.Ok(Unit), add(valid().copy(deletedAt = 7L, createdAt = 1L, updatedAt = 1L)))
            val saved = repo.stored.getValue("tx")
            assertEquals(now.toEpochMilli(), saved.createdAt)
            assertEquals(now.toEpochMilli(), saved.updatedAt)
            assertEquals(null, saved.deletedAt)
            assertEquals(1, runner.writes)
        }

    @Test
    fun `timestamp bounds are inclusive`() =
        runTest {
            add(valid(at = Instant.ofEpochMilli(TransactionRules.MIN_TIMESTAMP_MS)))
            add(valid(at = limit))
            assertEquals(2, repo.writes)
        }

    @Test
    fun `timestamp before 2000 is InvalidTimestamp`() =
        runTest {
            assertRejected(
                TransactionError.InvalidTimestamp,
                valid(at = Instant.ofEpochMilli(TransactionRules.MIN_TIMESTAMP_MS - 1)),
            )
        }

    @Test
    fun `timestamp beyond 366 days ahead is InvalidTimestamp`() =
        runTest {
            assertRejected(TransactionError.InvalidTimestamp, valid(at = limit.plusMillis(1)))
        }

    @Test
    fun `unknown source account is AccountUnusable`() =
        runTest {
            assertRejected(TransactionError.AccountUnusable, valid().copy(accountId = "ghost"))
        }

    @Test
    fun `unknown destination account is AccountUnusable`() =
        runTest {
            assertRejected(
                TransactionError.AccountUnusable,
                tx(TransactionType.TRANSFER, 1_000, to = "ghost", at = now.toEpochMilli()),
            )
        }

    @Test
    fun `transfer between live accounts is accepted`() =
        runTest {
            add(tx(TransactionType.TRANSFER, 1_000, to = B, at = now.toEpochMilli()))
            assertEquals(1, repo.writes)
        }

    @Test
    fun `expense needs an expense category`() =
        runTest {
            add(valid().copy(categoryId = "food"))
            assertEquals(1, repo.writes)
            assertRejected(TransactionError.CategoryTypeMismatch, valid().copy(categoryId = "salary"))
        }

    @Test
    fun `income needs an income category`() =
        runTest {
            add(valid(TransactionType.INCOME).copy(categoryId = "salary"))
            assertEquals(1, repo.writes)
            val onFood = valid(TransactionType.INCOME).copy(categoryId = "food")
            assertRejected(TransactionError.CategoryTypeMismatch, onFood)
        }

    @Test
    fun `unknown category is CategoryNotFound`() =
        runTest {
            assertRejected(TransactionError.CategoryNotFound, valid().copy(categoryId = "ghost"))
        }

    @Test
    fun `amount near Long MAX that overflows the balance returns AmountOverflow`() =
        runTest {
            repo.failWrite = ArithmeticException("long overflow")

            assertEquals(Outcome.Err(TransactionError.AmountOverflow), add(valid().copy(amount = Long.MAX_VALUE)))
            assertEquals(0, repo.writes)
            assertEquals(1, runner.writes)
        }
}
