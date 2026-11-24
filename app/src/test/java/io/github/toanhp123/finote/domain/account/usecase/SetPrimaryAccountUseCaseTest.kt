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

class SetPrimaryAccountUseCaseTest {
    private val now = Instant.parse("2026-11-03T00:00:00Z")
    private val repo = FakeAccountRepository()
    private val star = SetPrimaryAccountUseCase(repo, FakeTransactionRunner(), Clock.fixed(now, ZoneOffset.UTC))

    @Test
    fun `star moves to the chosen usable account`() =
        runTest {
            repo.stored["a"] = testAccount(id = "a").copy(isPrimary = true)
            repo.stored["b"] = testAccount(id = "b")

            assertEquals(Outcome.Ok(Unit), star("b"))

            assertEquals(false, repo.stored.getValue("a").isPrimary)
            assertEquals(true, repo.stored.getValue("b").isPrimary)
        }

    @Test
    fun `star on an archived account is Unusable`() =
        runTest {
            repo.stored["a"] = testAccount(id = "a").copy(isArchived = true)

            assertEquals(Outcome.Err(AccountError.Unusable), star("a"))
            assertEquals(0, repo.writes)
        }

    @Test
    fun `star on an ignored account is Unusable`() =
        runTest {
            repo.stored["a"] = testAccount(id = "a").copy(isIgnored = true)

            assertEquals(Outcome.Err(AccountError.Unusable), star("a"))
        }

    @Test
    fun `star on a missing or deleted account is NotFound`() =
        runTest {
            repo.stored["gone"] = testAccount(id = "gone").copy(deletedAt = 5L)

            assertEquals(Outcome.Err(AccountError.NotFound), star("ghost"))
            assertEquals(Outcome.Err(AccountError.NotFound), star("gone"))
            assertEquals(0, repo.writes)
        }
}
