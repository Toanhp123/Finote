package io.github.toanhp123.finote.data.category.local.dao

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.toanhp123.finote.data.category.local.entity.CategoryKeywordRuleEntity
import io.github.toanhp123.finote.data.database.InMemoryDatabaseRule
import io.github.toanhp123.finote.domain.category.model.KeywordMatchType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private fun userRule(
    id: String,
    categoryId: String,
    keyword: String = id,
) = CategoryKeywordRuleEntity(
    id = id,
    keyword = keyword,
    normalizedKeyword = keyword,
    matchType = KeywordMatchType.CONTAINS,
    normalizedMerchantName = null,
    categoryId = categoryId,
    priority = 0,
    isUserDefined = true,
    isActive = true,
    version = 0,
    createdAt = 1L,
    updatedAt = 1L,
)

@RunWith(AndroidJUnit4::class)
class CategoryKeywordRuleDaoTest {
    @get:Rule
    val dbRule = InMemoryDatabaseRule(seedSystemCategories = true, seedKeywordRules = true)

    private val dao get() = dbRule.db.keywordRuleDao()

    // `kw_highlands` is a system rule on `sys_food_coffee`.
    private suspend fun seed() {
        dao.upsert(userRule("u-coffee", "sys_food_coffee"))
        dao.upsert(userRule("u-restaurants", "sys_food_restaurants"))
    }

    @Test
    fun `moveToCategory moves user rules only`() =
        runTest {
            seed()

            dao.moveToCategory(setOf("sys_food_coffee"), "sys_food_groceries")

            assertEquals("sys_food_groceries", dao.getById("u-coffee")?.categoryId)
            assertEquals("sys_food_restaurants", dao.getById("u-restaurants")?.categoryId)
            assertEquals("sys_food_coffee", dao.getById("kw_highlands")?.categoryId)
        }

    @Test
    fun `deleteByCategories deletes user rules only`() =
        runTest {
            seed()

            dao.deleteByCategories(setOf("sys_food_coffee"))

            assertNull(dao.getById("u-coffee"))
            assertNotNull(dao.getById("u-restaurants"))
            assertNotNull(dao.getById("kw_highlands"))
        }

    @Test
    fun `countByCategories counts user rules only`() =
        runTest {
            seed()

            assertEquals(1, dao.countByCategories(setOf("sys_food_coffee")))
            assertEquals(2, dao.countByCategories(setOf("sys_food_coffee", "sys_food_restaurants")))
            assertEquals(0, dao.countByCategories(setOf("sys_food_groceries")))
        }

    @Test
    fun `deleteDuplicatesOf drops user rules whose keyword the target already has`() =
        runTest {
            dao.upsert(userRule("u-dup", "sys_food_coffee", keyword = "grab"))
            dao.upsert(userRule("u-keep", "sys_food_coffee", keyword = "cafe"))
            dao.upsert(userRule("u-target", "sys_food_groceries", keyword = "grab"))
            dao.upsert(userRule("u-other", "sys_food_restaurants", keyword = "grab"))

            dao.deleteDuplicatesOf(setOf("sys_food_coffee"), "sys_food_groceries")

            assertNull(dao.getById("u-dup"))
            assertNotNull(dao.getById("u-keep"))
            assertNotNull(dao.getById("u-target"))
            assertNotNull(dao.getById("u-other"))
        }
}
