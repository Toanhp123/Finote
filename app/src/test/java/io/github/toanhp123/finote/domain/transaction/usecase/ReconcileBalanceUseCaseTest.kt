package io.github.toanhp123.finote.domain.transaction.usecase

import io.github.toanhp123.finote.domain.account.A
import io.github.toanhp123.finote.domain.account.adjustment
import io.github.toanhp123.finote.domain.account.model.DriftKey
import io.github.toanhp123.finote.domain.account.model.digest
import io.github.toanhp123.finote.domain.account.usecase.FakeDriftAcknowledgementRepository
import io.github.toanhp123.finote.domain.common.FakeTransactionRunner
import io.github.toanhp123.finote.domain.common.Outcome
import io.github.toanhp123.finote.domain.common.TransactionRunner
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
    private val acks = FakeDriftAcknowledgementRepository()
    private val reconcile = ReconcileBalanceUseCase(repo, acks, runner, clock)

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

    @Test
    fun `zero difference acknowledges the current drift key`() =
        runTest {
            repo.reconcileResult = null
            repo.driftKey = DriftKey(5L, 1_000L)

            reconcile(A, 1L)

            assertEquals(mapOf(A to DriftKey(5L, 1_000L).digest(A)), acks.acknowledged.value)
        }

    @Test
    fun `zero difference with no drift acknowledges nothing`() =
        runTest {
            repo.driftKey = null

            reconcile(A, 1L)

            assertEquals(emptyMap<String, String>(), acks.acknowledged.value)
        }

    @Test
    fun `non-zero difference does not acknowledge`() =
        runTest {
            repo.reconcileResult = adjustment(AdjustmentDirection.IN, 1)
            repo.driftKey = DriftKey(5L, 1_000L)

            reconcile(A, 1L)

            assertEquals(emptyMap<String, String>(), acks.acknowledged.value)
        }

    @Test
    fun `unusable account acknowledges nothing`() =
        runTest {
            repo.driftKey = DriftKey(5L, 1_000L)

            reconcile("ghost", 1L)

            assertEquals(emptyMap<String, String>(), acks.acknowledged.value)
        }

    @Test
    fun `the drift key is read inside the write transaction`() =
        runTest {
            val insideRunner =
                object : TransactionRunner {
                    var inside = false

                    override suspend fun <T, E> write(block: suspend () -> Outcome<T, E>): Outcome<T, E> {
                        inside = true
                        try {
                            return block()
                        } finally {
                            inside = false
                        }
                    }
                }
            var readInside: Boolean? = null
            repo.driftKey = DriftKey(5L, 1_000L)
            repo.onGetDriftKey = { readInside = insideRunner.inside }

            ReconcileBalanceUseCase(repo, acks, insideRunner, clock)(A, 1L)

            assertEquals(true, readInside)
            assertEquals(mapOf(A to DriftKey(5L, 1_000L).digest(A)), acks.acknowledged.value)
        }

    @Test
    fun `a drift key that overflows returns AmountOverflow and acknowledges nothing`() =
        runTest {
            repo.onGetDriftKey = { throw ArithmeticException("long overflow") }

            assertEquals(Outcome.Err(TransactionError.AmountOverflow), reconcile(A, 1L))
            assertEquals(emptyMap<String, String>(), acks.acknowledged.value)
        }
}
