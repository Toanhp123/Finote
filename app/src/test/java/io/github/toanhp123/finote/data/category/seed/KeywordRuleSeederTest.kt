package io.github.toanhp123.finote.data.category.seed

import android.content.Context
import androidx.room3.Room
import androidx.room3.useWriterConnection
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.toanhp123.finote.data.database.FinoteDatabase
import io.github.toanhp123.finote.data.database.InMemoryDatabaseRule
import io.github.toanhp123.finote.domain.category.model.CategoryType
import io.github.toanhp123.finote.domain.category.model.KeywordMatchType
import io.github.toanhp123.finote.domain.common.normalizeKeyword
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

private val MULTI_PURPOSE = setOf("momo", "zalopay", "shopee", "lazada", "tiki")

@RunWith(AndroidJUnit4::class)
class KeywordRuleSeederTest {
    @get:Rule
    val dbRule = InMemoryDatabaseRule(seedSystemCategories = true, seedKeywordRules = true)

    private val dao get() = dbRule.db.keywordRuleDao()

    private fun seed(id: String) = SystemKeywordRules.first { it.id == id }

    @Test
    fun `new database gets every system rule`() =
        runTest {
            val stored = dao.getAll()

            assertEquals(SystemKeywordRules.map { it.id }.toSet(), stored.map { it.id }.toSet())
            assertEquals(SystemKeywordRules.size, stored.size)
            assertEquals(SystemKeywordRules.toSet(), stored.toSet())
        }

    @Test
    fun `onOpen inserts missing rules`() =
        runTest {
            withFile("keyword-seed-missing.db") { file ->
                withDb(file) { db ->
                    db.useWriterConnection {
                        it.usePrepared("DELETE FROM category_keyword_rules WHERE id = 'kw_kfc'") { s -> s.step() }
                    }
                }

                withDb(file) { db ->
                    val ids =
                        db
                            .keywordRuleDao()
                            .getAll()
                            .map { it.id }
                            .toSet()
                    assertEquals(SystemKeywordRules.map { it.id }.toSet(), ids)
                    assertEquals(seed("kw_kfc"), db.keywordRuleDao().getById("kw_kfc"))
                }
            }
        }

    @Test
    fun `a higher version updates the rule but keeps isActive false`() =
        runTest {
            withFile("keyword-seed-version.db") { file ->
                withDb(file) { db ->
                    val old = seed("kw_kfc").copy(version = 0, keyword = "old", categoryId = "sys_food")
                    db.keywordRuleDao().upsert(old.copy(isActive = false, createdAt = 77L))
                }

                withDb(file) { db ->
                    val row = checkNotNull(db.keywordRuleDao().getById("kw_kfc"))
                    assertEquals(seed("kw_kfc").copy(isActive = false, createdAt = 77L), row)
                }
            }
        }

    @Test
    fun `a rule of the same version is left alone`() =
        runTest {
            withFile("keyword-seed-same.db") { file ->
                withDb(file) { db -> db.keywordRuleDao().upsert(seed("kw_kfc").copy(keyword = "mine")) }

                withDb(file) { db -> assertEquals("mine", db.keywordRuleDao().getById("kw_kfc")?.keyword) }
            }
        }

    @Test
    fun `a user rule is never touched`() =
        runTest {
            withFile("keyword-seed-user.db") { file ->
                // A user rule that carries a system id and an old version: the seeder must not claim it.
                val mine = seed("kw_kfc").copy(version = 0, keyword = "mine", isUserDefined = true)
                withDb(file) { db -> db.keywordRuleDao().upsert(mine) }

                withDb(file) { db -> assertEquals(mine, db.keywordRuleDao().getById("kw_kfc")) }
            }
        }

    @Test
    fun `every system rule points at an existing sys category and none is a wallet or marketplace keyword`() =
        runTest {
            val categories =
                dbRule.db
                    .categoryDao()
                    .observeCategories()
                    .first()
                    .associateBy { it.id }

            SystemKeywordRules.forEach { rule ->
                val category = categories[rule.categoryId]
                assertTrue(rule.id, category != null && category.isSystemDefault && category.parentId != null)
                assertEquals(rule.id, CategoryType.EXPENSE, category?.type)
                assertEquals(rule.id, KeywordMatchType.CONTAINS, rule.matchType)
                assertFalse(rule.id, rule.isUserDefined)
                assertTrue(rule.id, rule.isActive)
                assertEquals(rule.id, 1, rule.version)
                assertEquals(rule.id, normalizeKeyword(rule.keyword), rule.normalizedKeyword)
                assertTrue(rule.id, rule.normalizedKeyword.length >= 3)
                assertFalse(rule.id, rule.normalizedKeyword in MULTI_PURPOSE)
            }
            assertEquals(SystemKeywordRules.size, SystemKeywordRules.map { it.id }.toSet().size)
            assertTrue(SystemKeywordRules.size in 15..20)
        }

    private suspend fun withFile(
        name: String,
        block: suspend (File) -> Unit,
    ) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = context.getDatabasePath(name).apply { parentFile?.mkdirs() }
        file.delete()
        try {
            block(file)
        } finally {
            file.delete()
        }
    }

    /** Opens the file database with both seeders, runs [block], closes it. A second call is a reopen. */
    private suspend fun withDb(
        file: File,
        block: suspend (FinoteDatabase) -> Unit,
    ) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db =
            Room
                .databaseBuilder<FinoteDatabase>(context, file.path)
                .setDriver(AndroidSQLiteDriver())
                .addCallback(SystemCategorySeeder(Clock.fixed(Instant.EPOCH, ZoneOffset.UTC)))
                .addCallback(KeywordRuleSeeder())
                .build()
        try {
            block(db)
        } finally {
            db.close()
        }
    }
}
