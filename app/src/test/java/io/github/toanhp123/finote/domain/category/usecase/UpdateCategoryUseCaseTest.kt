package io.github.toanhp123.finote.domain.category.usecase

import io.github.toanhp123.finote.domain.category.error.CategoryError
import io.github.toanhp123.finote.domain.category.model.Category
import io.github.toanhp123.finote.domain.category.model.CategoryType
import io.github.toanhp123.finote.domain.category.testCategory
import io.github.toanhp123.finote.domain.common.FakeTransactionRunner
import io.github.toanhp123.finote.domain.common.Outcome
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class UpdateCategoryUseCaseTest {
    private val now = Instant.parse("2026-11-03T00:00:00Z")
    private val repo = FakeCategoryRepository()
    private val runner = FakeTransactionRunner()
    private val update = UpdateCategoryUseCase(repo, runner, Clock.fixed(now, ZoneOffset.UTC))

    private suspend fun assertRejected(
        error: CategoryError,
        category: Category,
    ) {
        assertEquals(Outcome.Err(error), update(category))
        assertEquals(0, repo.writes)
    }

    @Test
    fun `missing or soft-deleted row is NotFound`() =
        runTest {
            assertRejected(CategoryError.NotFound, testCategory(id = "ghost"))
            repo.stored["cat-1"] = testCategory().copy(deletedAt = 5L)
            assertRejected(CategoryError.NotFound, testCategory())
        }

    @Test
    fun `blank name is rejected`() =
        runTest {
            repo.stored["cat-1"] = testCategory()

            assertRejected(CategoryError.BlankName, testCategory(name = " "))
        }

    @Test
    fun `type change is rejected`() =
        runTest {
            repo.stored["cat-1"] = testCategory()

            assertRejected(CategoryError.TypeChanged, testCategory(type = CategoryType.INCOME))
        }

    @Test
    fun `parent change of a system category is rejected`() =
        runTest {
            repo.stored["top"] = testCategory(id = "top")
            repo.stored["cat-1"] = testCategory(parentId = "top").copy(isSystemDefault = true)

            assertRejected(CategoryError.SystemParentChanged, testCategory(parentId = null))
        }

    @Test
    fun `self, missing and nested parents are rejected`() =
        runTest {
            repo.stored["cat-1"] = testCategory()
            repo.stored["child"] = testCategory(id = "child", parentId = "root")

            assertRejected(CategoryError.InvalidParent, testCategory(parentId = "cat-1"))
            assertRejected(CategoryError.InvalidParent, testCategory(parentId = "ghost"))
            assertRejected(CategoryError.InvalidParent, testCategory(parentId = "child"))
        }

    @Test
    fun `giving a parent to a category with live children is rejected`() =
        runTest {
            repo.stored["cat-1"] = testCategory()
            repo.stored["cat-2"] = testCategory(id = "cat-2", parentId = "cat-1")
            repo.stored["top"] = testCategory(id = "top")

            assertRejected(CategoryError.HasChildren, testCategory(parentId = "top"))
        }

    @Test
    fun `valid category is stamped with the clock time`() =
        runTest {
            repo.stored["cat-1"] = testCategory()

            val outcome = update(testCategory(name = "Drinks").copy(updatedAt = 2L))

            assertEquals(Outcome.Ok(Unit), outcome)
            assertEquals(now.toEpochMilli(), repo.stored.getValue("cat-1").updatedAt)
            assertEquals(1, repo.writes)
            assertEquals(1, runner.writes)
        }
}
