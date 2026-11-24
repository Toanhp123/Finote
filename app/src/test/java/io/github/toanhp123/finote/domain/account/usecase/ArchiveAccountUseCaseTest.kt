package io.github.toanhp123.finote.domain.account.usecase

import io.github.toanhp123.finote.domain.account.error.AccountError
import io.github.toanhp123.finote.domain.account.testAccount
import io.github.toanhp123.finote.domain.common.FakeTransactionRunner
import io.github.toanhp123.finote.domain.common.Outcome
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class ArchiveAccountUseCaseTest {
    private val now = Instant.parse("2026-11-03T00:00:00Z")
    private val repo = FakeAccountRepository()
    private val runner = FakeTransactionRunner()
    private val archive = ArchiveAccountUseCase(repo, runner, Clock.fixed(now, ZoneOffset.UTC))

    @Test
    fun `archive and unarchive stamp updatedAt from the clock`() =
        runTest {
            repo.stored["acc-1"] = testAccount()
            repo.stored["acc-2"] = testAccount(id = "acc-2")

            assertEquals(Outcome.Ok(Unit), archive("acc-1", archived = true))
            assertEquals(true, repo.stored.getValue("acc-1").isArchived)
            assertEquals(now.toEpochMilli(), repo.stored.getValue("acc-1").updatedAt)

            assertEquals(Outcome.Ok(Unit), archive("acc-1", archived = false))
            assertEquals(false, repo.stored.getValue("acc-1").isArchived)
            assertEquals(2, repo.writes)
            assertEquals(2, runner.writes)
        }

    @Test
    fun `archiving the last usable account is refused`() =
        runTest {
            repo.stored["acc-1"] = testAccount()
            repo.stored["ignored"] = testAccount(id = "ignored").copy(isIgnored = true)
            repo.stored["old"] = testAccount(id = "old").copy(isArchived = true)

            assertEquals(Outcome.Err(AccountError.LastUsableAccount), archive("acc-1", archived = true))
            assertEquals(0, repo.writes)
        }

    @Test
    fun `restoring is allowed when no account is usable`() =
        runTest {
            repo.stored["acc-1"] = testAccount().copy(isArchived = true)

            assertEquals(Outcome.Ok(Unit), archive("acc-1", archived = false))
        }

    @Test
    fun `archiving the star clears it`() =
        runTest {
            repo.stored["acc-1"] = testAccount().copy(isPrimary = true)
            repo.stored["acc-2"] = testAccount(id = "acc-2")

            archive("acc-1", archived = true)

            assertEquals(false, repo.stored.getValue("acc-1").isPrimary)
        }

    @Test
    fun `missing or deleted account is NotFound and nothing is written`() =
        runTest {
            repo.stored["gone"] = testAccount(id = "gone").copy(deletedAt = 5L)

            assertEquals(Outcome.Err(AccountError.NotFound), archive("ghost", archived = true))
            assertEquals(Outcome.Err(AccountError.NotFound), archive("gone", archived = true))
            assertEquals(0, repo.writes)
        }
}
