package io.github.toanhp123.finote.data.category.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.toanhp123.finote.data.category.local.entity.CategoryEntity
import io.github.toanhp123.finote.data.category.mapper.toDomain
import io.github.toanhp123.finote.data.database.InMemoryDatabaseRule
import io.github.toanhp123.finote.data.database.account
import io.github.toanhp123.finote.data.database.category
import io.github.toanhp123.finote.data.database.transaction
import io.github.toanhp123.finote.data.database.userKeywordRule
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
    fun `update giving a parent to a childless category works`() =
        runTest {
            seed(category(id = "cat-1"))

            repo.update(category(id = "cat-1", parentId = "sys_food").toDomain())

            assertEquals("sys_food", rowOf("cat-1")?.parentId)
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
    fun `update of a missing row does nothing`() =
        runTest {
            repo.update(category(id = "ghost").toDomain())

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
    fun `setHidden of a missing row does nothing`() =
        runTest {
            repo.setHidden("ghost", hidden = true, updatedAt = 8L)

            assertNull(rowOf("ghost"))
        }

    @Test
    fun `countLiveChildren ignores soft-deleted children`() =
        runTest {
            seed(
                category(id = "cat-1"),
                category(id = "cat-2", parentId = "cat-1"),
                category(id = "cat-3", parentId = "cat-1", deletedAt = 5L),
            )

            assertEquals(1, repo.countLiveChildren("cat-1"))
            assertEquals(0, repo.countLiveChildren("cat-2"))
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

    private suspend fun seedTransactions() {
        seed(category(id = "cat-a"), category(id = "cat-b"), category(id = "cat-child", parentId = "cat-a"))
        dbRule.db.accountDao().insert(account())
        val txs = dbRule.db.transactionDao()
        txs.insert(transaction(id = "tx-live", categoryId = "cat-a"))
        txs.insert(transaction(id = "tx-gone", categoryId = "cat-a").copy(deletedAt = 5L))
        txs.insert(transaction(id = "tx-pair", categoryId = "cat-a").copy(pairedIntoId = "tx-live"))
    }

    private suspend fun txCategory(id: String) =
        dbRule.db
            .transactionDao()
            .getById(id)
            ?.categoryId

    @Test
    fun `deleteAndMove moves live, deleted and paired rows`() =
        runTest {
            seedTransactions()

            repo.deleteAndMove(setOf("cat-a"), "cat-b", deletedAt = 8L)

            listOf("tx-live", "tx-gone", "tx-pair").forEach { assertEquals(it, "cat-b", txCategory(it)) }
        }

    @Test
    fun `deleteAndMove keeps updatedAt`() =
        runTest {
            seedTransactions()
            val before = checkNotNull(dbRule.db.transactionDao().getById("tx-live"))
            assertEquals(before.createdAt, before.updatedAt)

            repo.deleteAndMove(setOf("cat-a"), "cat-b", deletedAt = 8L)

            val after = checkNotNull(dbRule.db.transactionDao().getById("tx-live"))
            assertEquals(before.createdAt, after.createdAt)
            assertEquals(before.updatedAt, after.updatedAt)
        }

    @Test
    fun `deleteAndMove to null clears the category`() =
        runTest {
            seedTransactions()

            repo.deleteAndMove(setOf("cat-a"), null, deletedAt = 8L)

            assertNull(txCategory("tx-live"))
            assertNull(txCategory("tx-gone"))
        }

    @Test
    fun `deleteAndMove soft-deletes every id`() =
        runTest {
            seedTransactions()

            repo.deleteAndMove(setOf("cat-a", "cat-child"), "cat-b", deletedAt = 8L)

            assertEquals(8L, rowOf("cat-a")?.deletedAt)
            assertEquals(8L, rowOf("cat-child")?.deletedAt)
            assertNull(rowOf("cat-b")?.deletedAt)
        }

    @Test
    fun `deleteAndMove moves rules to the target`() =
        runTest {
            seedTransactions()
            val rules = dbRule.db.keywordRuleDao()
            rules.upsert(userKeywordRule("r-parent", "cat-a", keyword = "grab"))
            rules.upsert(userKeywordRule("r-child", "cat-child", keyword = "bus"))

            repo.deleteAndMove(setOf("cat-a", "cat-child"), "cat-b", deletedAt = 8L)

            assertEquals("cat-b", rules.getById("r-parent")?.categoryId)
            assertEquals("cat-b", rules.getById("r-child")?.categoryId)
        }

    @Test
    fun `a moved rule that duplicates a target rule of the same keyword is dropped, the target's rule stays`() =
        runTest {
            seedTransactions()
            val rules = dbRule.db.keywordRuleDao()
            rules.upsert(userKeywordRule("r-dup", "cat-a", keyword = "grab"))
            rules.upsert(userKeywordRule("r-new", "cat-a", keyword = "bus"))
            rules.upsert(userKeywordRule("r-target", "cat-b", keyword = "grab"))

            repo.deleteAndMove(setOf("cat-a"), "cat-b", deletedAt = 8L)

            assertNull(rules.getById("r-dup"))
            assertEquals("cat-b", rules.getById("r-new")?.categoryId)
            assertEquals("cat-b", rules.getById("r-target")?.categoryId)
        }

    @Test
    fun `deleteAndMove to null deletes the rules`() =
        runTest {
            seedTransactions()
            val rules = dbRule.db.keywordRuleDao()
            rules.upsert(userKeywordRule("r-a", "cat-a"))
            rules.upsert(userKeywordRule("r-b", "cat-b"))

            repo.deleteAndMove(setOf("cat-a"), null, deletedAt = 8L)

            assertNull(rules.getById("r-a"))
            assertNotNull(rules.getById("r-b"))
        }

    @Test
    fun `countRules counts user rules of the ids`() =
        runTest {
            seedTransactions()
            val rules = dbRule.db.keywordRuleDao()
            rules.upsert(userKeywordRule("r-a", "cat-a"))
            rules.upsert(userKeywordRule("r-child", "cat-child"))
            rules.upsert(userKeywordRule("r-b", "cat-b"))

            assertEquals(2, repo.countRules(setOf("cat-a", "cat-child")))
            assertEquals(0, repo.countRules(emptySet()))
        }

    @Test
    fun `countLiveTransactions ignores deleted rows`() =
        runTest {
            seedTransactions()

            assertEquals(2, repo.countLiveTransactions(setOf("cat-a")))
            assertEquals(0, repo.countLiveTransactions(setOf("cat-b", "cat-child")))
        }

    @Test
    fun `getLevel returns live rows of one level in order`() =
        runTest {
            seed(
                category(id = "p"),
                category(id = "y", parentId = "p", sortOrder = 1),
                category(id = "z", parentId = "p", sortOrder = 0),
                category(id = "x", parentId = "p", deletedAt = 3L),
            )

            assertEquals(listOf("z", "y"), repo.getLevel(CategoryType.EXPENSE, "p").map { it.id })
        }

    @Test
    fun `reorder writes index as sortOrder`() =
        runTest {
            seed(category(id = "c1"), category(id = "c2"), category(id = "c3"))

            repo.reorder(listOf("c3", "c1", "c2"), now = 8L)

            assertEquals(listOf(1, 2, 0), listOf("c1", "c2", "c3").map { rowOf(it)?.sortOrder })
            assertEquals(8L, rowOf("c3")?.updatedAt)
        }
}
