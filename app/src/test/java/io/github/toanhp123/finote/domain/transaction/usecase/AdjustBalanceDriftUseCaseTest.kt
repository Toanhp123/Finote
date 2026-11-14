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

class AdjustBalanceDriftUseCaseTest {
    private val now = Instant.parse("2026-11-04T00:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val repo = FakeTransactionRepository(activeAccounts = setOf(A))
    private val runner = FakeTransactionRunner()
    private val adjustDrift = AdjustBalanceDriftUseCase(repo, runner, clock)

    @Test
    fun `passes account and clock time to the repository`() =
        runTest {
            adjustDrift(A)

            assertEquals(1, runner.writes)
            val call = repo.adjustCalls.single()
            assertEquals(A, call.accountId)
            assertEquals(now.toEpochMilli(), call.now)
            UUID.fromString(call.id) // throws if not a UUID
        }

    @Test
    fun `each call gets a fresh id`() =
        runTest {
            adjustDrift(A)
            adjustDrift(A)

            val (first, second) = repo.adjustCalls
            assertNotEquals(first.id, second.id)
        }

    @Test
    fun `returns the repository result`() =
        runTest {
            val written = adjustment(AdjustmentDirection.IN, 1)
            repo.adjustResult = written
            assertEquals(Outcome.Ok(written), adjustDrift(A))

            repo.adjustResult = null
            assertEquals(Outcome.Ok(null), adjustDrift(A))
        }

    @Test
    fun `an unusable account is AccountUnusable and nothing is written`() =
        runTest {
            val result = adjustDrift("ghost")

            assertEquals(Outcome.Err(TransactionError.AccountUnusable), result)
            assertEquals(0, repo.adjustCalls.size)
        }

    @Test
    fun `a balance difference that overflows returns AmountOverflow`() =
        runTest {
            repo.failWrite = ArithmeticException("long overflow")

            assertEquals(Outcome.Err(TransactionError.AmountOverflow), adjustDrift(A))
            assertEquals(1, runner.writes)
        }
}
