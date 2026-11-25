package io.github.toanhp123.finote.domain.category.usecase

import io.github.toanhp123.finote.domain.category.error.CategoryError
import io.github.toanhp123.finote.domain.category.model.MoveTarget
import io.github.toanhp123.finote.domain.category.testCategory
import io.github.toanhp123.finote.domain.common.FakeTransactionRunner
import io.github.toanhp123.finote.domain.common.Outcome
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class DeleteCategoryUseCaseTest {
    private val now = Instant.parse("2026-11-03T00:00:00Z")
    private val repo = FakeCategoryRepository()
    private val runner = FakeTransactionRunner()
    private val delete = DeleteCategoryUseCase(repo, runner, Clock.fixed(now, ZoneOffset.UTC))

    private fun seedBranch() {
        repo.stored["a"] = testCategory(id = "a")
        repo.stored["a1"] = testCategory(id = "a1", parentId = "a")
        repo.stored["b"] = testCategory(id = "b")
    }

    @Test
    fun `delete of a parent deletes its live children and moves all their transactions`() =
        runTest {
            seedBranch()
            repo.transactionCategory["t-parent"] = "a"
            repo.transactionCategory["t-child"] = "a1"
            repo.transactionCategory["t-other"] = "b"

            val outcome = delete("a", MoveTarget.Into("b"))

            assertEquals(Outcome.Ok(Unit), outcome)
            assertEquals(now.toEpochMilli(), repo.stored.getValue("a").deletedAt)
            assertEquals(now.toEpochMilli(), repo.stored.getValue("a1").deletedAt)
            assertNull(repo.stored.getValue("b").deletedAt)
            assertEquals(mapOf("t-parent" to "b", "t-child" to "b", "t-other" to "b"), repo.transactionCategory)
            assertEquals(1, repo.writes)
            assertEquals(1, runner.writes)
        }

    @Test
    fun `delete to Uncategorized clears the category`() =
        runTest {
            seedBranch()
            repo.transactionCategory["t-1"] = "a"

            assertEquals(Outcome.Ok(Unit), delete("a", MoveTarget.Uncategorized))

            assertNull(repo.transactionCategory.getValue("t-1"))
            assertEquals(now.toEpochMilli(), repo.stored.getValue("a").deletedAt)
        }

    @Test
    fun `delete refuses a target inside the branch`() =
        runTest {
            seedBranch()
            repo.transactionCategory["t-1"] = "a"

            assertEquals(Outcome.Err(CategoryError.InvalidMoveTarget), delete("a", MoveTarget.Into("a1")))
            assertEquals(0, repo.writes)
            assertNull(repo.stored.getValue("a").deletedAt)
            assertEquals("a", repo.transactionCategory.getValue("t-1"))
        }

    @Test
    fun `delete refuses the category itself as target`() =
        runTest {
            seedBranch()

            assertEquals(Outcome.Err(CategoryError.InvalidMoveTarget), delete("a", MoveTarget.Into("a")))
            assertEquals(0, repo.writes)
        }

    @Test
    fun `delete refuses a missing or deleted target`() =
        runTest {
            seedBranch()
            repo.stored["gone"] = testCategory(id = "gone").copy(deletedAt = 5L)

            assertEquals(Outcome.Err(CategoryError.InvalidMoveTarget), delete("a", MoveTarget.Into("ghost")))
            assertEquals(Outcome.Err(CategoryError.InvalidMoveTarget), delete("a", MoveTarget.Into("gone")))
            assertEquals(0, repo.writes)
        }

    @Test
    fun `delete refuses a system category`() =
        runTest {
            repo.stored["cat-1"] = testCategory().copy(isSystemDefault = true)

            assertEquals(Outcome.Err(CategoryError.SystemCategory), delete("cat-1", MoveTarget.Uncategorized))
            assertEquals(0, repo.writes)
        }

    @Test
    fun `delete of a parent with a system child is refused`() =
        runTest {
            seedBranch()
            repo.stored["a1"] = testCategory(id = "a1", parentId = "a").copy(isSystemDefault = true)

            assertEquals(Outcome.Err(CategoryError.SystemCategory), delete("a", MoveTarget.Uncategorized))
            assertEquals(0, repo.writes)
        }

    @Test
    fun `delete of an unknown id is NotFound`() =
        runTest {
            assertEquals(Outcome.Err(CategoryError.NotFound), delete("ghost", MoveTarget.Uncategorized))
            assertEquals(0, repo.writes)
        }

    @Test
    fun `delete of a deleted category does nothing`() =
        runTest {
            repo.stored["cat-1"] = testCategory().copy(deletedAt = 5L)

            assertEquals(Outcome.Ok(Unit), delete("cat-1", MoveTarget.Uncategorized))
            assertEquals(5L, repo.stored.getValue("cat-1").deletedAt)
            assertEquals(0, repo.writes)
        }
}
