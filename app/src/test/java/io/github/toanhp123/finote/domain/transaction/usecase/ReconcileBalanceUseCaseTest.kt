package io.github.toanhp123.finote.domain.transaction.usecase

import io.github.toanhp123.finote.domain.account.A
import io.github.toanhp123.finote.domain.account.adjustment
import io.github.toanhp123.finote.domain.transaction.model.AdjustmentDirection
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class ReconcileBalanceUseCaseTest {
    private val now = Instant.parse("2026-11-04T00:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val repo = FakeTransactionRepository(activeAccounts = setOf(A))
    private val reconcile = ReconcileBalanceUseCase(repo, clock)

    @Test
    fun `passes account, figure and clock time to the repository`() =
        runTest {
            reconcile(A, 1_500_000L)

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
            assertSame(written, reconcile(A, 1L))

            repo.reconcileResult = null
            assertNull(reconcile(A, 1L))
        }
}
