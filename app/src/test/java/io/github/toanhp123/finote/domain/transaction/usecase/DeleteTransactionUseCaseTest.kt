package io.github.toanhp123.finote.domain.transaction.usecase

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

class DeleteTransactionUseCaseTest {
    private val now = Instant.parse("2026-11-03T00:00:00Z")
    private val repo = FakeTransactionRepository()
    private val runner = FakeTransactionRunner()
    private val delete = DeleteTransactionUseCase(repo, runner, Clock.fixed(now, ZoneOffset.UTC))
    private val stored = tx(TransactionType.EXPENSE, 100_000)

    @Test
    fun `delete marks the transaction with the clock time`() =
        runTest {
            repo.stored["tx"] = stored
            assertEquals(Outcome.Ok(Unit), delete("tx"))
            assertEquals(now.toEpochMilli(), repo.stored.getValue("tx").deletedAt)
            assertEquals(1, runner.writes)
        }

    @Test
    fun `deleting twice writes once`() =
        runTest {
            repo.stored["tx"] = stored
            assertEquals(Outcome.Ok(Unit), delete("tx"))
            assertEquals(Outcome.Ok(Unit), delete("tx"))
            assertEquals(1, repo.writes)
        }

    @Test
    fun `missing transaction is NotFound`() =
        runTest {
            assertEquals(Outcome.Err(TransactionError.NotFound), delete("ghost"))
            assertEquals(0, repo.writes)
        }

    @Test
    fun `an overflow while rebuilding the balance returns AmountOverflow`() =
        runTest {
            repo.stored["tx"] = stored
            repo.failWrite = ArithmeticException("long overflow")

            assertEquals(Outcome.Err(TransactionError.AmountOverflow), delete("tx"))
            assertEquals(null, repo.stored.getValue("tx").deletedAt)
        }
}
