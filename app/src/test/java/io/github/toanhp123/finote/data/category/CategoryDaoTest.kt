package io.github.toanhp123.finote.data.category

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.toanhp123.finote.data.InMemoryDatabaseRule
import io.github.toanhp123.finote.data.category
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CategoryDaoTest {
    @get:Rule
    val dbRule = InMemoryDatabaseRule()

    private val dao get() = dbRule.db.categoryDao()

    @Test
    fun `category round-trips every column`() =
        runTest {
            dao.insert(category(id = "cat-parent"))
            val child =
                category(id = "cat-child", parentId = "cat-parent", name = "Ăn uống")
                    .copy(nameKey = "category_food")
            dao.insert(child)
            assertEquals(child, dao.getById("cat-child"))
        }

    @Test
    fun `update changes the stored row`() =
        runTest {
            val original = category()
            dao.insert(original)
            val changed = original.copy(name = "Renamed", nameKey = null, updatedAt = 1_700_000_001_000L)
            dao.update(changed)
            assertEquals(changed, dao.getById(original.id))
        }

    @Test
    fun `insert with unknown parent fails`() =
        runTest {
            val message = runCatching { dao.insert(category(parentId = "missing")) }.exceptionOrNull()?.message
            assertNotNull(message)
            assertTrue(message.orEmpty(), message.orEmpty().contains("FOREIGN KEY constraint failed"))
        }

    @Test
    fun `hard-deleting a parent with children fails`() =
        runTest {
            dao.insert(category(id = "cat-parent"))
            dao.insert(category(id = "cat-child", parentId = "cat-parent"))
            val delete = "DELETE FROM categories WHERE id = 'cat-parent'"
            val message = runCatching { dbRule.execSql(delete) }.exceptionOrNull()?.message
            assertNotNull(message)
            assertTrue(message.orEmpty(), message.orEmpty().contains("FOREIGN KEY constraint failed"))
            assertNotNull(dao.getById("cat-parent"))
        }

    @Test
    fun `observeCategories hides soft-deleted rows but getById returns them`() =
        runTest {
            dao.insert(category(id = "cat-late", sortOrder = 1, createdAt = 3_000L))
            dao.insert(category(id = "cat-tie-new", sortOrder = 0, createdAt = 2_000L))
            dao.insert(category(id = "cat-tie-old", sortOrder = 0, createdAt = 1_000L))
            dao.insert(category(id = "cat-gone", sortOrder = 0, createdAt = 500L, deletedAt = 9_000L))
            val ids = dao.observeCategories().first().map { it.id }
            assertEquals(listOf("cat-tie-old", "cat-tie-new", "cat-late"), ids)
            assertNotNull(dao.getById("cat-gone"))
        }

    @Test
    fun `default rule does not seed categories`() =
        runTest {
            assertTrue(dao.observeCategories().first().isEmpty())
        }

    @Test
    fun `isHidden round-trips`() =
        runTest {
            val hidden = category().copy(isHidden = true)
            dao.insert(hidden)
            assertEquals(hidden, dao.getById(hidden.id))
        }

    @Test
    fun `countLiveChildren ignores soft-deleted children`() =
        runTest {
            dao.insert(category(id = "p"))
            dao.insert(category(id = "c1", parentId = "p"))
            dao.insert(category(id = "c2", parentId = "p", deletedAt = 9L))
            assertEquals(1, dao.countLiveChildren("p"))
            assertEquals(0, dao.countLiveChildren("c1"))
        }
}
