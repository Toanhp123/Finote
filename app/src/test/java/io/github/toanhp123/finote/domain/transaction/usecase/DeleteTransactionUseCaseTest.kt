package io.github.toanhp123.finote.domain.transaction.usecase

import io.github.toanhp123.finote.domain.account.tx
import io.github.toanhp123.finote.domain.transaction.model.TransactionType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class DeleteTransactionUseCaseTest {
    private val now = Instant.parse("2026-11-03T00:00:00Z")
    private val repo = FakeTransactionRepository()
    private val delete = DeleteTransactionUseCase(repo, Clock.fixed(now, ZoneOffset.UTC))
    private val stored = tx(TransactionType.EXPENSE, 100_000)

    @Test
    fun `delete marks the transaction with the clock time`() =
        runTest {
            repo.stored["tx"] = stored
            delete("tx")
            assertEquals(now.toEpochMilli(), repo.stored.getValue("tx").deletedAt)
        }

    @Test
    fun `deleting twice writes once`() =
        runTest {
            repo.stored["tx"] = stored
            delete("tx")
            delete("tx")
            assertEquals(1, repo.writes)
        }

    @Test
    fun `missing transaction is rejected`() =
        runTest {
            val failure = runCatching { delete("ghost") }.exceptionOrNull()
            assertTrue(failure.toString(), failure is IllegalArgumentException)
            assertEquals(0, repo.writes)
        }
}
