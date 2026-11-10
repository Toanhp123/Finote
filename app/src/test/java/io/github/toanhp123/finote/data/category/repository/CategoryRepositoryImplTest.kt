package io.github.toanhp123.finote.data.category.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.toanhp123.finote.data.category.local.entity.CategoryEntity
import io.github.toanhp123.finote.data.category.mapper.toDomain
import io.github.toanhp123.finote.data.database.InMemoryDatabaseRule
import io.github.toanhp123.finote.data.database.account
import io.github.toanhp123.finote.data.database.category
import io.github.toanhp123.finote.data.database.transaction
import io.github.toanhp123.finote.domain.category.model.Category
import io.github.toanhp123.finote.domain.category.model.CategoryType
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

@RunWith(AndroidJUnit4::class)
class CategoryRepositoryImplTest {
    // Seeded, so the system categories sys_food, sys_food_coffee and sys_income_salary exist.
    @get:Rule
    val dbRule = InMemoryDatabaseRule(seedSystemCategories = true)

    private lateinit var repo: CategoryRepositoryImpl

    @Before
    fun setUp() {
        repo = CategoryRepositoryImpl(dbRule.db)
    }

    private val dao get() = dbRule.db.categoryDao()

    private suspend fun seed(vararg rows: CategoryEntity) = rows.forEach { dao.insert(it) }

    private suspend fun rowOf(id: String) = dao.getById(id)

    private suspend fun failureOf(block: suspend () -> Unit) = runCatching { block() }.exceptionOrNull()

    /** The call must throw [IllegalArgumentException] and leave every given row exactly as it was. */
    private suspend fun assertRejected(
        vararg ids: String,
        block: suspend () -> Unit,
    ) {
        val before = ids.map { rowOf(it) }
        val failure = failureOf(block)
        assertTrue(failure.toString(), failure is IllegalArgumentException)
        assertEquals(before, ids.map { rowOf(it) })
    }

    @Test
    fun `add forces isSystemDefault false, nameKey null, isHidden false`() =
        runTest {
            repo.add(category().toDomain().copy(isSystemDefault = true, nameKey = "x", isHidden = true))

            val stored = checkNotNull(rowOf("cat-1"))
            assertFalse(stored.isSystemDefault)
            assertNull(stored.nameKey)
            assertFalse(stored.isHidden)
            assertEquals(stored.toDomain(), repo.getCategory("cat-1"))
            assertNull(repo.getCategory("ghost"))
        }

    @Test
    fun `add with a live top-level parent of the same type works`() =
        runTest {
            repo.add(category(id = "cat-2", parentId = "sys_food").toDomain())

            assertEquals("sys_food", rowOf("cat-2")?.parentId)
        }

    @Test
    fun `add with a child as parent is rejected`() =
        runTest {
            assertRejected("cat-2") { repo.add(category(id = "cat-2", parentId = "sys_food_coffee").toDomain()) }
            assertNull(rowOf("cat-2"))
        }

    @Test
    fun `add with a parent of the other type is rejected`() =
        runTest {
            assertRejected { repo.add(category(id = "cat-2", parentId = "sys_income_salary").toDomain()) }
            assertNull(rowOf("cat-2"))
        }

    @Test
    fun `add with a soft-deleted parent is rejected`() =
        runTest {
            seed(category(id = "cat-1", deletedAt = 5L))

            assertRejected { repo.add(category(id = "cat-2", parentId = "cat-1").toDomain()) }
            assertNull(rowOf("cat-2"))
        }

    @Test
    fun `add with a missing parent is rejected`() =
        runTest {
            assertRejected { repo.add(category(id = "cat-2", parentId = "ghost").toDomain()) }
            assertNull(rowOf("cat-2"))
        }

    @Test
    fun `update setting parentId to itself is rejected`() =
        runTest {
            seed(category(id = "cat-1"))

            assertRejected("cat-1") { repo.update(category(id = "cat-1", parentId = "cat-1").toDomain()) }
        }

    @Test
    fun `update giving a parent to a category with live children is rejected`() =
        runTest {
            seed(category(id = "cat-1"), category(id = "cat-2", parentId = "cat-1"))

            assertRejected("cat-1", "cat-2") {
                repo.update(category(id = "cat-1", parentId = "sys_food").toDomain())
            }
        }

    @Test
    fun `update giving a parent to a childless category works`() =
        runTest {
            seed(category(id = "cat-1"))

            repo.update(category(id = "cat-1", parentId = "sys_food").toDomain())

            assertEquals("sys_food", rowOf("cat-1")?.parentId)
        }

    @Test
    fun `update with an invalid parent is rejected`() =
        runTest {
            seed(category(id = "cat-1"))

            assertRejected("cat-1") { repo.update(category(id = "cat-1", parentId = "sys_food_coffee").toDomain()) }
            assertRejected("cat-1") { repo.update(category(id = "cat-1", parentId = "sys_income_salary").toDomain()) }
        }

    @Test
    fun `update changing type is rejected`() =
        runTest {
            seed(category(id = "cat-1"))

            assertRejected("cat-1") { repo.update(category().toDomain().copy(type = CategoryType.INCOME)) }
        }

    @Test
    fun `update keeps nameKey when name is unchanged`() =
        runTest {
            val food = checkNotNull(rowOf("sys_food"))

            repo.update(food.toDomain().copy(color = 0x11, updatedAt = 8L))

            val after = checkNotNull(rowOf("sys_food"))
            assertEquals("sys_food", after.nameKey)
            assertEquals(0x11, after.color)
            assertEquals(8L, after.updatedAt)
        }

    @Test
    fun `update clears nameKey when name changes`() =
        runTest {
            val food = checkNotNull(rowOf("sys_food"))

            repo.update(food.toDomain().copy(name = "Ăn uống"))

            val after = checkNotNull(rowOf("sys_food"))
            assertEquals("Ăn uống", after.name)
            assertNull(after.nameKey)
            assertTrue(after.isSystemDefault)
        }

    @Test
    fun `update keeps isSystemDefault, isHidden, createdAt, deletedAt of the stored row`() =
        runTest {
            seed(category(id = "cat-1").copy(isHidden = true))
            val before = checkNotNull(rowOf("cat-1"))

            repo.update(
                before.toDomain().copy(
                    name = "Renamed",
                    isSystemDefault = true,
                    isHidden = false,
                    createdAt = 99L,
                    updatedAt = 8L,
                    deletedAt = 7L,
                ),
            )

            val after = checkNotNull(rowOf("cat-1"))
            assertEquals("Renamed", after.name)
            assertEquals(8L, after.updatedAt)
            assertFalse(after.isSystemDefault)
            assertTrue(after.isHidden)
            assertEquals(before.createdAt, after.createdAt)
            assertNull(after.deletedAt)
        }

    @Test
    fun `update changing parentId of a system category is rejected`() =
        runTest {
            val coffee = checkNotNull(rowOf("sys_food_coffee")).toDomain()

            assertRejected("sys_food_coffee") { repo.update(coffee.copy(parentId = "sys_other")) }
            assertRejected("sys_food_coffee") { repo.update(coffee.copy(parentId = null)) }
        }

    @Test
    fun `update of a missing or soft-deleted row is rejected`() =
        runTest {
            seed(category(id = "cat-1", deletedAt = 5L))

            assertRejected("cat-1") { repo.update(category(id = "cat-1", name = "New").toDomain()) }
            assertRejected { repo.update(category(id = "ghost").toDomain()) }
            assertNull(rowOf("ghost"))
        }

    @Test
    fun `setHidden flips isHidden and updatedAt, works on system categories, and back`() =
        runTest {
            repo.setHidden("sys_food", hidden = true, updatedAt = 8L)
            val hidden = checkNotNull(rowOf("sys_food"))
            assertTrue(hidden.isHidden)
            assertEquals(8L, hidden.updatedAt)
            assertTrue(hidden.isSystemDefault)

            repo.setHidden("sys_food", hidden = false, updatedAt = 9L)
            val shown = checkNotNull(rowOf("sys_food"))
            assertFalse(shown.isHidden)
            assertEquals(9L, shown.updatedAt)
        }

    @Test
    fun `setHidden of a missing or soft-deleted row is rejected`() =
        runTest {
            seed(category(id = "cat-1", deletedAt = 5L))

            assertRejected("cat-1") { repo.setHidden("cat-1", hidden = true, updatedAt = 8L) }
            assertRejected { repo.setHidden("ghost", hidden = true, updatedAt = 8L) }
        }

    @Test
    fun `softDelete of a system category is rejected`() =
        runTest {
            assertRejected("sys_other") { repo.softDelete("sys_other", deletedAt = 8L) }
            assertRejected("sys_food_coffee") { repo.softDelete("sys_food_coffee", deletedAt = 8L) }
        }

    @Test
    fun `softDelete of a parent with live children is rejected, then works after the children go`() =
        runTest {
            seed(category(id = "cat-1"), category(id = "cat-2", parentId = "cat-1"))

            assertRejected("cat-1", "cat-2") { repo.softDelete("cat-1", deletedAt = 8L) }

            repo.softDelete("cat-2", deletedAt = 8L)
            repo.softDelete("cat-1", deletedAt = 9L)

            assertEquals(9L, rowOf("cat-1")?.deletedAt)
        }

    @Test
    fun `softDelete of a category with transactions succeeds and getCategory still returns it`() =
        runTest {
            seed(category(id = "cat-1"))
            dbRule.db.accountDao().insert(account())
            dbRule.db.transactionDao().insert(transaction(categoryId = "cat-1"))

            repo.softDelete("cat-1", deletedAt = 8L)

            assertEquals(8L, repo.getCategory("cat-1")?.deletedAt)
            assertEquals(
                "cat-1",
                dbRule.db
                    .transactionDao()
                    .getById("tx-1")
                    ?.categoryId,
            )
        }

    @Test
    fun `softDelete sets deletedAt and updatedAt, second call does nothing`() =
        runTest {
            seed(category(id = "cat-1"))

            repo.softDelete("cat-1", deletedAt = 8L)
            repo.softDelete("cat-1", deletedAt = 9L)

            val after = checkNotNull(rowOf("cat-1"))
            assertEquals(8L, after.deletedAt)
            assertEquals(8L, after.updatedAt)
        }

    @Test
    fun `softDelete of a missing id is rejected`() =
        runTest {
            assertRejected { repo.softDelete("ghost", deletedAt = 8L) }
            assertNull(rowOf("ghost"))
        }

    @Test
    fun `observeCategories includes hidden rows and excludes deleted rows`() =
        runTest {
            seed(
                category(id = "cat-1"),
                category(id = "cat-2").copy(isHidden = true),
                category(id = "cat-3", deletedAt = 5L),
            )

            val ids = repo.observeCategories().first().map(Category::id)

            assertTrue("cat-1" in ids)
            assertTrue("cat-2" in ids)
            assertFalse("cat-3" in ids)
            assertNotNull(repo.getCategory("cat-3"))
        }
}
