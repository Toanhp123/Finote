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

class AdjustBalanceDriftUseCaseTest {
    private val now = Instant.parse("2026-11-04T00:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val repo = FakeTransactionRepository(activeAccounts = setOf(A))
    private val adjustDrift = AdjustBalanceDriftUseCase(repo, clock)

    @Test
    fun `passes account and clock time to the repository`() =
        runTest {
            adjustDrift(A)

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
            assertSame(written, adjustDrift(A))

            repo.adjustResult = null
            assertNull(adjustDrift(A))
        }
}
