package io.github.toanhp123.finote.domain.category.usecase

import io.github.toanhp123.finote.domain.category.error.CategoryError
import io.github.toanhp123.finote.domain.category.testCategory
import io.github.toanhp123.finote.domain.common.FakeTransactionRunner
import io.github.toanhp123.finote.domain.common.Outcome
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class DeleteCategoryUseCaseTest {
    private val now = Instant.parse("2026-11-03T00:00:00Z")
    private val repo = FakeCategoryRepository()
    private val runner = FakeTransactionRunner()
    private val delete = DeleteCategoryUseCase(repo, runner, Clock.fixed(now, ZoneOffset.UTC))

    @Test
    fun `delete marks the category with the clock time`() =
        runTest {
            repo.stored["cat-1"] = testCategory()

            val outcome = delete("cat-1")

            assertEquals(Outcome.Ok(Unit), outcome)
            assertEquals(now.toEpochMilli(), repo.stored.getValue("cat-1").deletedAt)
            assertEquals(1, repo.writes)
            assertEquals(1, runner.writes)
        }

    @Test
    fun `unknown id is NotFound`() =
        runTest {
            assertEquals(Outcome.Err(CategoryError.NotFound), delete("ghost"))
            assertEquals(0, repo.writes)
        }

    @Test
    fun `already deleted row is a no-op Ok`() =
        runTest {
            repo.stored["cat-1"] = testCategory().copy(deletedAt = 5L)

            assertEquals(Outcome.Ok(Unit), delete("cat-1"))
            assertEquals(5L, repo.stored.getValue("cat-1").deletedAt)
            assertEquals(0, repo.writes)
        }

    @Test
    fun `system category is rejected`() =
        runTest {
            repo.stored["cat-1"] = testCategory().copy(isSystemDefault = true)

            assertEquals(Outcome.Err(CategoryError.SystemCategory), delete("cat-1"))
            assertEquals(0, repo.writes)
        }

    @Test
    fun `parent with live children is rejected, then works after the children go`() =
        runTest {
            repo.stored["cat-1"] = testCategory()
            repo.stored["cat-2"] = testCategory(id = "cat-2", parentId = "cat-1")

            assertEquals(Outcome.Err(CategoryError.HasChildren), delete("cat-1"))
            assertEquals(0, repo.writes)

            delete("cat-2")
            assertEquals(Outcome.Ok(Unit), delete("cat-1"))
        }
}
