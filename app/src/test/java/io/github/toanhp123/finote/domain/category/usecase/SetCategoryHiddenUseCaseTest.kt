package io.github.toanhp123.finote.domain.category.usecase

import io.github.toanhp123.finote.domain.category.testCategory
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class SetCategoryHiddenUseCaseTest {
    private val now = Instant.parse("2026-11-03T00:00:00Z")
    private val repo = FakeCategoryRepository()
    private val setHidden = SetCategoryHiddenUseCase(repo, Clock.fixed(now, ZoneOffset.UTC))

    @Test
    fun `hide and show stamp updatedAt from the clock`() =
        runTest {
            repo.stored["cat-1"] = testCategory()

            setHidden("cat-1", hidden = true)
            assertEquals(true, repo.stored.getValue("cat-1").isHidden)
            assertEquals(now.toEpochMilli(), repo.stored.getValue("cat-1").updatedAt)

            setHidden("cat-1", hidden = false)
            assertEquals(false, repo.stored.getValue("cat-1").isHidden)
            assertEquals(2, repo.writes)
        }
}
