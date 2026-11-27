package io.github.toanhp123.finote.data.budget.local.dao

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.toanhp123.finote.data.budget.local.entity.BudgetEntity
import io.github.toanhp123.finote.data.budget.local.entity.BudgetMonthEntity
import io.github.toanhp123.finote.data.database.InMemoryDatabaseRule
import io.github.toanhp123.finote.data.database.category
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private fun budget(
    id: String,
    monthYear: String,
    categoryId: String? = "cat-food",
) = BudgetEntity(
    id = id,
    categoryId = categoryId,
    limitAmount = 3_000_000L,
    monthYear = monthYear,
    isRollover = false,
    alertThreshold = 80,
    lastAlertSentThreshold = null,
    createdAt = 1L,
    updatedAt = 1L,
)

@RunWith(AndroidJUnit4::class)
class BudgetDaoTest {
    @get:Rule
    val dbRule = InMemoryDatabaseRule()

    private val dao get() = dbRule.db.budgetDao()

    @Before
    fun insertCategories() =
        runTest {
            dbRule.db.categoryDao().insert(category(id = "cat-food"))
            dbRule.db.categoryDao().insert(category(id = "cat-travel"))
        }

    @Test
    fun `budget round-trips every column`() =
        runTest {
            val row = budget("b-1", "2026-11").copy(isRollover = true, alertThreshold = 90, lastAlertSentThreshold = 80)
            dao.insert(row)

            assertEquals(row, dao.getById("b-1"))
        }

    @Test
    fun `two rows for one category in one month are refused`() =
        runTest {
            dao.insert(budget("b-1", "2026-11"))

            val message = runCatching { dao.insert(budget("b-2", "2026-11")) }.exceptionOrNull()?.message

            assertNotNull(message)
            assertTrue(message.orEmpty(), message.orEmpty().contains("UNIQUE constraint failed"))
            assertEquals(listOf("b-1"), dao.getByMonth("2026-11").map { it.id })
        }

    @Test
    fun `the same category may have a budget in another month`() =
        runTest {
            dao.insert(budget("b-1", "2026-10"))
            dao.insert(budget("b-2", "2026-11"))
            dao.insert(budget("b-3", "2026-11", categoryId = null))

            assertEquals(listOf("b-2", "b-3"), dao.getByMonth("2026-11").map { it.id })
        }

    @Test
    fun `observeUpTo excludes later months`() =
        runTest {
            dao.insert(budget("b-old", "2026-09"))
            dao.insert(budget("b-now", "2026-11"))
            dao.insert(budget("b-next", "2026-12"))

            assertEquals(listOf("b-old", "b-now"), dao.observeUpTo("2026-11").first().map { it.id })
        }

    @Test
    fun `latestSetUpBefore returns the closest earlier month`() =
        runTest {
            listOf("2026-05", "2026-09", "2026-11", "2026-12").forEach { dao.markSetUp(BudgetMonthEntity(it)) }

            assertEquals("2026-09", dao.latestSetUpBefore("2026-11"))
            assertNull(dao.latestSetUpBefore("2026-05"))
        }

    @Test
    fun `markSetUp twice keeps one row and isSetUp reads it`() =
        runTest {
            dao.markSetUp(BudgetMonthEntity("2026-11"))
            dao.markSetUp(BudgetMonthEntity("2026-11"))

            assertTrue(dao.isSetUp("2026-11"))
            assertFalse(dao.isSetUp("2026-10"))
            assertEquals("2026-11", dao.observeFirstSetUp().first())
        }

    @Test
    fun `observeFirstSetUp is null before any month is set up`() =
        runTest { assertNull(dao.observeFirstSetUp().first()) }

    @Test
    fun `a category with a budget cannot be hard-deleted`() =
        runTest {
            dao.insert(budget("b-1", "2026-11"))

            val delete = "DELETE FROM categories WHERE id = 'cat-food'"
            val message = runCatching { dbRule.execSql(delete) }.exceptionOrNull()?.message

            assertNotNull(message)
            assertTrue(message.orEmpty(), message.orEmpty().contains("FOREIGN KEY constraint failed"))
            assertNotNull(dbRule.db.categoryDao().getById("cat-food"))
        }

    @Test
    fun `update changes the row and deleteById removes it`() =
        runTest {
            dao.insert(budget("b-1", "2026-11"))
            val changed = budget("b-1", "2026-11").copy(limitAmount = 5_000_000L, updatedAt = 2L)

            dao.update(changed)
            assertEquals(changed, dao.getById("b-1"))

            dao.deleteById("b-1")
            assertNull(dao.getById("b-1"))
        }

    @Test
    fun `count get and delete by categories`() =
        runTest {
            dao.insert(budget("b-1", "2026-10"))
            dao.insert(budget("b-2", "2026-11"))
            dao.insert(budget("b-3", "2026-11", categoryId = "cat-travel"))
            dao.insert(budget("b-4", "2026-11", categoryId = null))

            assertEquals(2, dao.countByCategories(setOf("cat-food")))
            assertEquals(3, dao.countByCategories(setOf("cat-food", "cat-travel")))
            assertEquals(listOf("b-1", "b-2"), dao.getByCategories(setOf("cat-food")).map { it.id })

            dao.deleteByIds(listOf("b-1", "b-3"))

            assertEquals(listOf("b-2", "b-4"), dao.getByMonth("2026-11").map { it.id })
            assertNull(dao.getById("b-1"))
        }
}
