package io.github.toanhp123.finote.domain.transaction.usecase

import io.github.toanhp123.finote.domain.account.tx
import io.github.toanhp123.finote.domain.common.FakeTransactionRunner
import io.github.toanhp123.finote.domain.common.Outcome
import io.github.toanhp123.finote.domain.transaction.error.TransactionError
import io.github.toanhp123.finote.domain.transaction.model.Transaction
import io.github.toanhp123.finote.domain.transaction.model.TransactionType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class UnpairTransferUseCaseTest {
    private val now = Instant.parse("2026-11-03T00:00:00Z").toEpochMilli()
    private val clock = Clock.fixed(Instant.ofEpochMilli(now), ZoneOffset.UTC)
    private val runner = FakeTransactionRunner()
    private val repo = FakeTransactionRepository(activeAccounts = setOf("acc-a", "acc-b"))
    private val pairs = FakeTransferPairRepository(repo)
    private val unpair = UnpairTransferUseCase(repo, pairs, runner, clock)

    private fun transfer(deletedAt: Long? = null) =
        tx(TransactionType.TRANSFER, 500_000, account = "acc-a", to = "acc-b").copy(id = "t", deletedAt = deletedAt)

    private fun original(
        id: String,
        type: TransactionType,
        account: String,
    ): Transaction =
        tx(type, 500_000, account = account).copy(
            id = id,
            deletedAt = 5L,
            createdAt = 10L,
            updatedAt = 10L,
            pairedIntoId = "t",
        )

    private fun storePair() {
        repo.stored["t"] = transfer()
        repo.stored["o-1"] = original("o-1", TransactionType.EXPENSE, "acc-a")
        repo.stored["o-2"] = original("o-2", TransactionType.INCOME, "acc-b")
    }

    @Test
    fun `unpair soft-deletes the transfer and restores both originals`() =
        runTest {
            storePair()

            assertEquals(Outcome.Ok(Unit), unpair("t"))

            assertEquals(now, repo.stored.getValue("t").deletedAt)
            listOf("o-1", "o-2").forEach {
                val row = repo.stored.getValue(it)
                assertNull(row.deletedAt)
                assertNull(row.pairedIntoId)
                assertEquals(now, row.updatedAt)
            }
            assertEquals(1, runner.writes)
        }

    @Test
    fun `an archived original account is AccountUnusable and nothing changes`() =
        runTest {
            val archivedRepo = FakeTransactionRepository(activeAccounts = setOf("acc-a"))
            val archivedPairs = FakeTransferPairRepository(archivedRepo)
            archivedRepo.stored.putAll(
                mapOf(
                    "t" to transfer(),
                    "o-1" to original("o-1", TransactionType.EXPENSE, "acc-a"),
                    "o-2" to original("o-2", TransactionType.INCOME, "acc-b"),
                ),
            )
            val before = archivedRepo.stored.toMap()

            val result = UnpairTransferUseCase(archivedRepo, archivedPairs, runner, clock)("t")

            assertEquals(Outcome.Err(TransactionError.AccountUnusable), result)
            assertEquals(before, archivedRepo.stored)
            assertEquals(0, archivedPairs.unpairs)
        }

    @Test
    fun `an unknown id is NotFound`() =
        runTest {
            assertEquals(Outcome.Err(TransactionError.NotFound), unpair("ghost"))
        }

    @Test
    fun `a manual transfer with no originals is NotFound`() =
        runTest {
            repo.stored["t"] = transfer()

            assertEquals(Outcome.Err(TransactionError.NotFound), unpair("t"))
            assertEquals(0, pairs.unpairs)
        }

    @Test
    fun `a soft-deleted transfer is NotFound`() =
        runTest {
            storePair()
            repo.stored["t"] = transfer(deletedAt = 7L)

            assertEquals(Outcome.Err(TransactionError.NotFound), unpair("t"))
            assertEquals(0, pairs.unpairs)
        }

    @Test
    fun `an overflow while rebuilding returns AmountOverflow`() =
        runTest {
            storePair()
            pairs.failWrite = ArithmeticException("long overflow")

            assertEquals(Outcome.Err(TransactionError.AmountOverflow), unpair("t"))
            assertNull(repo.stored.getValue("t").deletedAt)
        }
}
