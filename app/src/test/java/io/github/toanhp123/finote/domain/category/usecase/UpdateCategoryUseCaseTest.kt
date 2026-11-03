package io.github.toanhp123.finote.domain.category.usecase

import io.github.toanhp123.finote.domain.category.testCategory
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class UpdateCategoryUseCaseTest {
    private val now = Instant.parse("2026-11-03T00:00:00Z")
    private val repo = FakeCategoryRepository()
    private val update = UpdateCategoryUseCase(repo, ValidateCategoryUseCase(), Clock.fixed(now, ZoneOffset.UTC))

    @Test
    fun `blank name is rejected and nothing is written`() =
        runTest {
            val failure = runCatching { update(testCategory(name = " ")) }.exceptionOrNull()

            assertTrue(failure.toString(), failure is IllegalArgumentException)
            assertEquals(0, repo.writes)
        }

    @Test
    fun `valid category is stamped with the clock time`() =
        runTest {
            update(testCategory().copy(updatedAt = 2L))

            assertEquals(now.toEpochMilli(), repo.stored.getValue("cat-1").updatedAt)
            assertEquals(1, repo.writes)
        }
}
