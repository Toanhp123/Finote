package io.github.toanhp123.finote.domain.account.usecase

import io.github.toanhp123.finote.domain.account.testAccount
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class DeleteAccountUseCaseTest {
    private val now = Instant.parse("2026-11-03T00:00:00Z")
    private val repo = FakeAccountRepository()
    private val delete = DeleteAccountUseCase(repo, Clock.fixed(now, ZoneOffset.UTC))

    @Test
    fun `delete marks the account with the clock time`() =
        runTest {
            repo.stored["acc-1"] = testAccount()

            delete("acc-1")

            assertEquals(now.toEpochMilli(), repo.stored.getValue("acc-1").deletedAt)
            assertEquals(1, repo.writes)
        }
}
