package io.github.toanhp123.finote.data.category.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.toanhp123.finote.data.category.mapper.toDomain
import io.github.toanhp123.finote.data.database.InMemoryDatabaseRule
import io.github.toanhp123.finote.data.database.assertStorageFailure
import io.github.toanhp123.finote.data.database.category
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** A SQLite failure in any category call reaches callers as a StorageException. */
@RunWith(AndroidJUnit4::class)
class CategoryRepositoryStorageErrorTest {
    @get:Rule
    val dbRule = InMemoryDatabaseRule()

    @Test
    fun `read maps SQLException to StorageException`() =
        runTest {
            val repo = CategoryRepositoryImpl(dbRule.db)
            dbRule.execSql("DROP TABLE categories")

            assertStorageFailure { repo.getCategory("cat-1") }
        }

    @Test
    fun `write maps SQLException to StorageException`() =
        runTest {
            val repo = CategoryRepositoryImpl(dbRule.db)
            dbRule.execSql("DROP TABLE categories")

            assertStorageFailure { repo.add(category().toDomain()) }
        }

    @Test
    fun `observe maps SQLException to StorageException`() =
        runTest {
            val repo = CategoryRepositoryImpl(dbRule.db)
            dbRule.execSql("DROP TABLE categories")

            assertStorageFailure { repo.observeCategories().first() }
        }
}
