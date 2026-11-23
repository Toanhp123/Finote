package io.github.toanhp123.finote.domain.account.usecase

import io.github.toanhp123.finote.domain.account.error.AccountError
import io.github.toanhp123.finote.domain.account.testAccount
import io.github.toanhp123.finote.domain.common.FakeTransactionRunner
import io.github.toanhp123.finote.domain.common.Outcome
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class DeleteAccountUseCaseTest {
    private val now = Instant.parse("2026-11-03T00:00:00Z")
    private val repo = FakeAccountRepository()
    private val runner = FakeTransactionRunner()
    private val delete = DeleteAccountUseCase(repo, runner, Clock.fixed(now, ZoneOffset.UTC))

    @Test
    fun `delete marks the account with the clock time`() =
        runTest {
            repo.stored["acc-1"] = testAccount()

            assertEquals(Outcome.Ok(Unit), delete("acc-1"))

            assertEquals(now.toEpochMilli(), repo.stored.getValue("acc-1").deletedAt)
            assertEquals(1, repo.writes)
            assertEquals(1, runner.writes)
        }

    @Test
    fun `unknown id is NotFound`() =
        runTest {
            assertEquals(Outcome.Err(AccountError.NotFound), delete("ghost"))
            assertEquals(0, repo.writes)
        }

    @Test
    fun `already deleted account is a no-op Ok`() =
        runTest {
            repo.stored["acc-1"] = testAccount().copy(deletedAt = 5L)
            repo.liveCounts["acc-1"] = 3

            assertEquals(Outcome.Ok(Unit), delete("acc-1"))
            assertEquals(5L, repo.stored.getValue("acc-1").deletedAt)
            assertEquals(0, repo.writes)
        }

    @Test
    fun `account with live transactions is HasTransactions, then works after they go`() =
        runTest {
            repo.stored["acc-1"] = testAccount()
            repo.liveCounts["acc-1"] = 2

            assertEquals(Outcome.Err(AccountError.HasTransactions), delete("acc-1"))
            assertEquals(0, repo.writes)
            assertNull(repo.stored.getValue("acc-1").deletedAt)

            repo.liveCounts["acc-1"] = 0
            assertEquals(Outcome.Ok(Unit), delete("acc-1"))
        }
}
