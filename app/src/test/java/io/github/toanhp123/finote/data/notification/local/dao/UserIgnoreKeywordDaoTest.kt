package io.github.toanhp123.finote.data.notification.local.dao

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.toanhp123.finote.data.database.InMemoryDatabaseRule
import io.github.toanhp123.finote.data.notification.local.entity.UserIgnoreKeywordEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private fun keyword(
    id: String,
    packageName: String = "fake.bank",
    normalized: String = "fake ad",
) = UserIgnoreKeywordEntity(id, packageName, "Fake Ad", normalized, 1L)

@RunWith(AndroidJUnit4::class)
class UserIgnoreKeywordDaoTest {
    @get:Rule
    val dbRule = InMemoryDatabaseRule()

    private val dao get() = dbRule.db.userIgnoreKeywordDao()

    @Test
    fun `same normalized keyword twice in one package is refused`() =
        runTest {
            dao.insert(keyword("k1"))

            assertNotNull(runCatching { dao.insert(keyword("k2")) }.exceptionOrNull())
            assertEquals(listOf("k1"), dao.getByPackage("fake.bank").map { it.id })
        }

    @Test
    fun `same keyword in two packages is fine`() =
        runTest {
            dao.insert(keyword("k1"))
            dao.insert(keyword("k2", packageName = "fake.wallet"))

            assertEquals(listOf("k1"), dao.getByPackage("fake.bank").map { it.id })
            assertEquals(listOf("k2"), dao.getByPackage("fake.wallet").map { it.id })
            assertEquals(
                setOf("k1", "k2"),
                dao
                    .observeAll()
                    .first()
                    .map { it.id }
                    .toSet(),
            )
        }

    @Test
    fun `delete removes the row and reports the count`() =
        runTest {
            dao.insert(keyword("k1"))

            assertEquals(1, dao.delete("k1"))
            assertEquals(0, dao.delete("k1"))
            assertNull(dao.getById("k1"))
        }
}
