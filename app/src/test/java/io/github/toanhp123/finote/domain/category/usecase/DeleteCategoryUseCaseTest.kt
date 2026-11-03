package io.github.toanhp123.finote.domain.category.usecase

import io.github.toanhp123.finote.domain.category.testCategory
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class DeleteCategoryUseCaseTest {
    private val now = Instant.parse("2026-11-03T00:00:00Z")
    private val repo = FakeCategoryRepository()
    private val delete = DeleteCategoryUseCase(repo, Clock.fixed(now, ZoneOffset.UTC))

    @Test
    fun `delete marks the category with the clock time`() =
        runTest {
            repo.stored["cat-1"] = testCategory()

            delete("cat-1")

            assertEquals(now.toEpochMilli(), repo.stored.getValue("cat-1").deletedAt)
            assertEquals(1, repo.writes)
        }
}
