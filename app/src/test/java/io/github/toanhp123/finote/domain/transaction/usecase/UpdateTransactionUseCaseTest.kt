package io.github.toanhp123.finote.domain.transaction.usecase

import io.github.toanhp123.finote.domain.account.A
import io.github.toanhp123.finote.domain.account.tx
import io.github.toanhp123.finote.domain.transaction.model.TransactionType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class UpdateTransactionUseCaseTest {
    private val now = Instant.parse("2026-11-03T00:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val repo = FakeTransactionRepository(activeAccounts = setOf(A))
    private val update = UpdateTransactionUseCase(repo, ValidateTransactionUseCase(repo, clock), clock)
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
            update(stored.copy(amount = 250_000, createdAt = 99L, updatedAt = 99L, deletedAt = 3L))
            val saved = repo.stored.getValue("tx")
            assertEquals(250_000L, saved.amount)
            assertEquals(5L, saved.createdAt)
            assertEquals(now.toEpochMilli(), saved.updatedAt)
            assertEquals(null, saved.deletedAt)
        }

    @Test
    fun `missing transaction is rejected`() =
        runTest {
            val failure = runCatching { update(stored) }.exceptionOrNull()
            assertTrue(failure.toString(), failure is IllegalArgumentException)
            assertEquals(0, repo.writes)
        }

    @Test
    fun `deleted transaction is rejected`() =
        runTest {
            repo.stored["tx"] = stored.copy(deletedAt = 1L)
            val failure = runCatching { update(stored) }.exceptionOrNull()
            assertTrue(failure.toString(), failure is IllegalArgumentException)
            assertEquals(0, repo.writes)
        }

    @Test
    fun `update runs the same validation as add`() =
        runTest {
            repo.stored["tx"] = stored
            val failure = runCatching { update(stored.copy(timestamp = 0L)) }.exceptionOrNull()
            assertTrue(failure.toString(), failure is IllegalArgumentException)
            assertEquals(0, repo.writes)
        }
}
