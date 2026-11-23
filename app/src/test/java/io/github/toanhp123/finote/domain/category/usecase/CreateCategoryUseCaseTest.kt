package io.github.toanhp123.finote.domain.category.usecase

import io.github.toanhp123.finote.domain.category.error.CategoryError
import io.github.toanhp123.finote.domain.category.model.Category
import io.github.toanhp123.finote.domain.category.model.CategoryType
import io.github.toanhp123.finote.domain.category.testCategory
import io.github.toanhp123.finote.domain.common.FakeTransactionRunner
import io.github.toanhp123.finote.domain.common.Outcome
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class CreateCategoryUseCaseTest {
    private val now = Instant.parse("2026-11-03T00:00:00Z")
    private val repo = FakeCategoryRepository()
    private val runner = FakeTransactionRunner()
    private val create = CreateCategoryUseCase(repo, runner, Clock.fixed(now, ZoneOffset.UTC))

    private suspend fun assertRejected(
        error: CategoryError,
        category: Category,
    ) {
        assertEquals(Outcome.Err(error), create(category))
        assertEquals(0, repo.writes)
    }

    @Test
    fun `blank name is rejected`() =
        runTest {
            assertRejected(CategoryError.BlankName, testCategory(name = ""))
            assertRejected(CategoryError.BlankName, testCategory(name = "   "))
        }

    @Test
    fun `missing parent is rejected`() =
        runTest {
            assertRejected(CategoryError.InvalidParent, testCategory(parentId = "ghost"))
        }

    @Test
    fun `soft-deleted, nested or other-type parent is rejected`() =
        runTest {
            repo.stored["gone"] = testCategory(id = "gone").copy(deletedAt = 5L)
            repo.stored["child"] = testCategory(id = "child", parentId = "root")
            repo.stored["income"] = testCategory(id = "income", type = CategoryType.INCOME)

            assertRejected(CategoryError.InvalidParent, testCategory(parentId = "gone"))
            assertRejected(CategoryError.InvalidParent, testCategory(parentId = "child"))
            assertRejected(CategoryError.InvalidParent, testCategory(parentId = "income"))
        }

    @Test
    fun `valid category gets a new uuid, clock timestamps and no deletedAt`() =
        runTest {
            val outcome = create(testCategory().copy(createdAt = 1L, updatedAt = 2L, deletedAt = 7L))

            val id = (outcome as Outcome.Ok).value
            UUID.fromString(id)
            assertNotEquals("cat-1", id)
            val saved = repo.stored.getValue(id)
            assertEquals(id, saved.id)
            assertEquals(now.toEpochMilli(), saved.createdAt)
            assertEquals(now.toEpochMilli(), saved.updatedAt)
            assertNull(saved.deletedAt)
            assertEquals(1, repo.writes)
            assertEquals(1, runner.writes)
        }

    @Test
    fun `child of a live top-level parent is stored`() =
        runTest {
            repo.stored["top"] = testCategory(id = "top")

            val outcome = create(testCategory(parentId = "top"))

            assertTrue(outcome is Outcome.Ok)
            assertEquals(1, runner.writes)
        }
}
