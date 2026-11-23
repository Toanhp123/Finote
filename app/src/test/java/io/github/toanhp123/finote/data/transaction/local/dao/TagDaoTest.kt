package io.github.toanhp123.finote.data.transaction.local.dao

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.toanhp123.finote.data.database.InMemoryDatabaseRule
import io.github.toanhp123.finote.data.database.account
import io.github.toanhp123.finote.data.database.tag
import io.github.toanhp123.finote.data.database.tagRef
import io.github.toanhp123.finote.data.database.transaction
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TagDaoTest {
    @get:Rule
    val dbRule = InMemoryDatabaseRule()

    private val dao get() = dbRule.db.tagDao()

    @Test
    fun `tag round-trips every column`() =
        runTest {
            val original = tag(name = "Du lịch")
            dao.insert(original)
            assertEquals(original, dao.getById(original.id))
        }

    @Test
    fun `update changes the stored row`() =
        runTest {
            val original = tag()
            dao.insert(original)
            val changed = original.copy(name = "Renamed", isArchived = true, updatedAt = 1_700_000_001_000L)
            dao.update(changed)
            assertEquals(changed, dao.getById(original.id))
        }

    @Test
    fun `observeTags hides soft-deleted rows and orders by name`() =
        runTest {
            dao.insert(tag(id = "tag-b", name = "Beta"))
            dao.insert(tag(id = "tag-a", name = "Alpha"))
            dao.insert(tag(id = "tag-gone", name = "Aardvark", deletedAt = 9_000L))
            assertEquals(listOf("tag-a", "tag-b"), dao.observeTags().first().map { it.id })
            assertNotNull(dao.getById("tag-gone"))
        }

    @Test
    fun `hard-deleting a tag removes its refs`() =
        runTest {
            val txDao = dbRule.db.transactionDao()
            dbRule.db.accountDao().insert(account())
            txDao.insert(transaction())
            dao.insert(tag())
            dao.insertTagRef(tagRef())
            assertEquals(1, dao.getTagRefs("tx-1").size)
            dbRule.execSql("DELETE FROM tags WHERE id = 'tag-1'")
            assertTrue(dao.getTagRefs("tx-1").isEmpty())
        }
}
