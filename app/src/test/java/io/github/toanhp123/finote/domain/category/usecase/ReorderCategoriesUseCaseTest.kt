package io.github.toanhp123.finote.domain.category.usecase

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

class ReorderCategoriesUseCaseTest {
    private val now = Instant.parse("2026-11-03T00:00:00Z")
    private val repo = FakeCategoryRepository()
    private val runner = FakeTransactionRunner()
    private val reorder = ReorderCategoriesUseCase(repo, runner, Clock.fixed(now, ZoneOffset.UTC))

    private fun orderOf(vararg ids: String) = ids.map { repo.stored.getValue(it).sortOrder }

    @Test
    fun `reorder appends unlisted rows of the level in their old order`() =
        runTest {
            repo.stored["a"] = testCategory(id = "a").copy(sortOrder = 0)
            repo.stored["h"] = testCategory(id = "h").copy(sortOrder = 1, isHidden = true)
            repo.stored["b"] = testCategory(id = "b").copy(sortOrder = 2)

            val outcome = reorder(CategoryType.EXPENSE, null, listOf("b", "a"))

            assertEquals(Outcome.Ok(Unit), outcome)
            assertEquals(listOf(0, 1, 2), orderOf("b", "a", "h"))
            assertEquals(now.toEpochMilli(), repo.stored.getValue("h").updatedAt)
            assertEquals(1, runner.writes)
        }

    @Test
    fun `reorder ignores ids outside the level and repeated ids`() =
        runTest {
            repo.stored["a"] = testCategory(id = "a").copy(sortOrder = 0)
            repo.stored["b"] = testCategory(id = "b").copy(sortOrder = 1)
            repo.stored["kid"] = testCategory(id = "kid", parentId = "a").copy(sortOrder = 7)
            repo.stored["inc"] = testCategory(id = "inc", type = CategoryType.INCOME).copy(sortOrder = 8)

            reorder(CategoryType.EXPENSE, null, listOf("kid", "b", "ghost", "inc", "b", "a"))

            assertEquals(listOf(0, 1), orderOf("b", "a"))
            assertEquals(7, repo.stored.getValue("kid").sortOrder)
            assertEquals(8, repo.stored.getValue("inc").sortOrder)
        }

    @Test
    fun `reorder of a child level leaves the top level alone`() =
        runTest {
            repo.stored["p"] = testCategory(id = "p").copy(sortOrder = 5)
            repo.stored["k1"] = testCategory(id = "k1", parentId = "p").copy(sortOrder = 0)
            repo.stored["k2"] = testCategory(id = "k2", parentId = "p").copy(sortOrder = 1)

            reorder(CategoryType.EXPENSE, "p", listOf("k2", "k1"))

            assertEquals(listOf(0, 1), orderOf("k2", "k1"))
            assertEquals(5, repo.stored.getValue("p").sortOrder)
        }
}
