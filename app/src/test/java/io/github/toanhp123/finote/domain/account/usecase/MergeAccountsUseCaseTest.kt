package io.github.toanhp123.finote.domain.account.usecase

import io.github.toanhp123.finote.domain.account.error.AccountError
import io.github.toanhp123.finote.domain.account.error.MergeRejection
import io.github.toanhp123.finote.domain.account.testAccount
import io.github.toanhp123.finote.domain.common.FakeTransactionRunner
import io.github.toanhp123.finote.domain.common.Outcome
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class MergeAccountsUseCaseTest {
    private val now = Instant.parse("2026-11-03T00:00:00Z")
    private val repo = FakeAccountRepository()
    private val runner = FakeTransactionRunner()
    private val merge = MergeAccountsUseCase(repo, runner, Clock.fixed(now, ZoneOffset.UTC))

    private fun bank(id: String) = testAccount(id = id).copy(bankPackageName = "vcb")

    private fun seedPair() {
        repo.stored["acc-1"] = bank("acc-1")
        repo.stored["acc-2"] = bank("acc-2")
    }

    private fun rejected(reason: MergeRejection) = Outcome.Err(AccountError.MergeRejected(reason))

    @Test
    fun `passes now from the clock`() =
        runTest {
            seedPair()

            assertEquals(Outcome.Ok(Unit), merge("acc-1", "acc-2"))

            assertEquals(listOf(Triple("acc-1", "acc-2", now.toEpochMilli())), repo.merges)
            assertEquals(1, runner.writes)
        }

    @Test
    fun `same id is rejected`() =
        runTest {
            seedPair()

            assertEquals(rejected(MergeRejection.SAME_ACCOUNT), merge("acc-1", "acc-1"))
            assertTrue(repo.merges.isEmpty())
        }

    @Test
    fun `missing or deleted account is rejected`() =
        runTest {
            seedPair()
            repo.stored["acc-3"] = bank("acc-3").copy(deletedAt = 5L)

            assertEquals(rejected(MergeRejection.ACCOUNT_GONE), merge("acc-1", "ghost"))
            assertEquals(rejected(MergeRejection.ACCOUNT_GONE), merge("acc-3", "acc-1"))
            assertEquals(0, repo.writes)
        }

    @Test
    fun `other bank is rejected`() =
        runTest {
            seedPair()
            repo.stored["acc-2"] = bank("acc-2").copy(bankPackageName = "mb")

            assertEquals(rejected(MergeRejection.DIFFERENT_BANK), merge("acc-1", "acc-2"))
            assertEquals(0, repo.writes)
        }

    @Test
    fun `transfer between the two is rejected in either direction`() =
        runTest {
            seedPair()
            repo.transferCounts[setOf("acc-1", "acc-2")] = 1

            assertEquals(rejected(MergeRejection.HAS_TRANSFER), merge("acc-1", "acc-2"))
            assertEquals(rejected(MergeRejection.HAS_TRANSFER), merge("acc-2", "acc-1"))
            assertEquals(0, repo.writes)
        }
}
