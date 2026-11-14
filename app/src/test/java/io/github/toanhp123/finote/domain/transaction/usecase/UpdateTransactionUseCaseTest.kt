package io.github.toanhp123.finote.domain.transaction.usecase

import io.github.toanhp123.finote.domain.account.A
import io.github.toanhp123.finote.domain.account.B
import io.github.toanhp123.finote.domain.account.tx
import io.github.toanhp123.finote.domain.common.FakeTransactionRunner
import io.github.toanhp123.finote.domain.common.Outcome
import io.github.toanhp123.finote.domain.transaction.error.TransactionError
import io.github.toanhp123.finote.domain.transaction.model.TransactionType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class UpdateTransactionUseCaseTest {
    private val now = Instant.parse("2026-11-03T00:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val repo = FakeTransactionRepository(activeAccounts = setOf(A))
    private val runner = FakeTransactionRunner()
    private val update = UpdateTransactionUseCase(repo, runner, clock)
    private val stored =
        tx(
            TransactionType.EXPENSE,
            100_000,
            at = now.toEpochMilli(),
        ).copy(createdAt = 5L, updatedAt = 5L)

    @Test
    fun `update keeps createdAt and stamps updatedAt`() =
        runTest {
            repo.stored["tx"] = stored
            val result = update(stored.copy(amount = 250_000, createdAt = 99L, updatedAt = 99L, deletedAt = 3L))
            assertEquals(Outcome.Ok(Unit), result)
            val saved = repo.stored.getValue("tx")
            assertEquals(250_000L, saved.amount)
            assertEquals(5L, saved.createdAt)
            assertEquals(now.toEpochMilli(), saved.updatedAt)
            assertEquals(null, saved.deletedAt)
            assertEquals(1, runner.writes)
        }

    @Test
    fun `missing transaction is NotFound`() =
        runTest {
            assertEquals(Outcome.Err(TransactionError.NotFound), update(stored))
            assertEquals(0, repo.writes)
        }

    @Test
    fun `deleted transaction is NotFound`() =
        runTest {
            repo.stored["tx"] = stored.copy(deletedAt = 1L)
            assertEquals(Outcome.Err(TransactionError.NotFound), update(stored))
            assertEquals(0, repo.writes)
        }

    @Test
    fun `update runs the same rules as add`() =
        runTest {
            repo.stored["tx"] = stored
            assertEquals(Outcome.Err(TransactionError.InvalidTimestamp), update(stored.copy(timestamp = 0L)))
            assertEquals(0, repo.writes)
        }

    @Test
    fun `moving a transaction to an unusable account is AccountUnusable before writing`() =
        runTest {
            repo.stored["tx"] = stored
            assertEquals(Outcome.Err(TransactionError.AccountUnusable), update(stored.copy(accountId = B)))
            assertEquals(0, repo.writes)
        }

    @Test
    fun `moving a transaction out of an unusable account is AccountUnusable before writing`() =
        runTest {
            val onlyB = FakeTransactionRepository(activeAccounts = setOf(B))
            val updateOnlyB = UpdateTransactionUseCase(onlyB, runner, clock)
            onlyB.stored["tx"] = stored
            assertEquals(Outcome.Err(TransactionError.AccountUnusable), updateOnlyB(stored.copy(accountId = B)))
            assertEquals(0, onlyB.writes)
        }

    @Test
    fun `amount near Long MAX that overflows the balance returns AmountOverflow`() =
        runTest {
            repo.stored["tx"] = stored
            repo.failWrite = ArithmeticException("long overflow")

            assertEquals(
                Outcome.Err(TransactionError.AmountOverflow),
                update(stored.copy(amount = Long.MAX_VALUE)),
            )
            assertEquals(100_000L, repo.stored.getValue("tx").amount)
        }
}
