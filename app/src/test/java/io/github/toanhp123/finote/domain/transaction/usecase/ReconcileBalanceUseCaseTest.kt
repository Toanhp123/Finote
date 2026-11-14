package io.github.toanhp123.finote.domain.transaction.usecase

import io.github.toanhp123.finote.domain.account.A
import io.github.toanhp123.finote.domain.account.adjustment
import io.github.toanhp123.finote.domain.common.FakeTransactionRunner
import io.github.toanhp123.finote.domain.common.Outcome
import io.github.toanhp123.finote.domain.transaction.error.TransactionError
import io.github.toanhp123.finote.domain.transaction.model.AdjustmentDirection
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class ReconcileBalanceUseCaseTest {
    private val now = Instant.parse("2026-11-04T00:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val repo = FakeTransactionRepository(activeAccounts = setOf(A))
    private val runner = FakeTransactionRunner()
    private val reconcile = ReconcileBalanceUseCase(repo, runner, clock)

    @Test
    fun `passes account, figure and clock time to the repository`() =
        runTest {
            reconcile(A, 1_500_000L)

            assertEquals(1, runner.writes)
            val call = repo.reconcileCalls.single()
            assertEquals(A, call.accountId)
            assertEquals(1_500_000L, call.actualBalance)
            assertEquals(now.toEpochMilli(), call.now)
            UUID.fromString(call.id) // throws if not a UUID
        }

    @Test
    fun `each call gets a fresh id`() =
        runTest {
            reconcile(A, 1L)
            reconcile(A, 1L)

            val (first, second) = repo.reconcileCalls
            assertNotEquals(first.id, second.id)
        }

    @Test
    fun `returns the repository result`() =
        runTest {
            val written = adjustment(AdjustmentDirection.IN, 1)
            repo.reconcileResult = written
            assertEquals(Outcome.Ok(written), reconcile(A, 1L))

            repo.reconcileResult = null
            assertEquals(Outcome.Ok(null), reconcile(A, 1L))
        }

    @Test
    fun `an unusable account is AccountUnusable and nothing is written`() =
        runTest {
            val result = reconcile("ghost", 1L)

            assertEquals(Outcome.Err(TransactionError.AccountUnusable), result)
            assertEquals(0, repo.reconcileCalls.size)
        }

    @Test
    fun `a balance difference that overflows returns AmountOverflow`() =
        runTest {
            repo.failWrite = ArithmeticException("long overflow")

            assertEquals(Outcome.Err(TransactionError.AmountOverflow), reconcile(A, 1L))
            assertEquals(1, runner.writes)
        }
}
