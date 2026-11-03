package io.github.toanhp123.finote.domain.account.usecase

import io.github.toanhp123.finote.domain.account.testAccount
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class ArchiveAccountUseCaseTest {
    private val now = Instant.parse("2026-11-03T00:00:00Z")
    private val repo = FakeAccountRepository()
    private val archive = ArchiveAccountUseCase(repo, Clock.fixed(now, ZoneOffset.UTC))

    @Test
    fun `archive and unarchive stamp updatedAt from the clock`() =
        runTest {
            repo.stored["acc-1"] = testAccount()

            archive("acc-1", archived = true)
            assertEquals(true, repo.stored.getValue("acc-1").isArchived)
            assertEquals(now.toEpochMilli(), repo.stored.getValue("acc-1").updatedAt)

            archive("acc-1", archived = false)
            assertEquals(false, repo.stored.getValue("acc-1").isArchived)
            assertEquals(2, repo.writes)
        }
}
