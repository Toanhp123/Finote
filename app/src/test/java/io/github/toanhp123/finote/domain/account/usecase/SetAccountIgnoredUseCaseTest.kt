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

class SetAccountIgnoredUseCaseTest {
    private val now = Instant.parse("2026-11-03T00:00:00Z")
    private val repo = FakeAccountRepository()
    private val runner = FakeTransactionRunner()
    private val setIgnored = SetAccountIgnoredUseCase(repo, runner, Clock.fixed(now, ZoneOffset.UTC))

    @Test
    fun `passes now from the clock`() =
        runTest {
            repo.stored["acc-1"] = testAccount()

            val outcome = setIgnored("acc-1", ignored = true, shouldTrashOldTransactions = true)

            assertEquals(Outcome.Ok(Unit), outcome)
            assertEquals(
                listOf(FakeAccountRepository.IgnoreCall("acc-1", true, true, now.toEpochMilli())),
                repo.ignores,
            )
            assertEquals(1, runner.writes)
        }

    @Test
    fun `missing or deleted account is NotFound and nothing is written`() =
        runTest {
            repo.stored["gone"] = testAccount(id = "gone").copy(deletedAt = 5L)

            val onMissing = setIgnored("ghost", ignored = true, shouldTrashOldTransactions = true)
            val onDeleted = setIgnored("gone", ignored = false, shouldTrashOldTransactions = false)

            assertEquals(Outcome.Err(AccountError.NotFound), onMissing)
            assertEquals(Outcome.Err(AccountError.NotFound), onDeleted)
            assertEquals(0, repo.writes)
        }
}
