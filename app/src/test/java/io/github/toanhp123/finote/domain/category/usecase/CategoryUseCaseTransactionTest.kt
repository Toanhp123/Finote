package io.github.toanhp123.finote.domain.category.usecase

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.toanhp123.finote.data.category.mapper.toDomain
import io.github.toanhp123.finote.data.category.repository.CategoryRepositoryImpl
import io.github.toanhp123.finote.data.database.InMemoryDatabaseRule
import io.github.toanhp123.finote.data.database.RoomTransactionRunner
import io.github.toanhp123.finote.data.database.category
import io.github.toanhp123.finote.domain.category.error.CategoryError
import io.github.toanhp123.finote.domain.common.Outcome
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/** The category use cases on the real Room runner: the check and the write share one transaction. */
@RunWith(AndroidJUnit4::class)
class CategoryUseCaseTransactionTest {
    @get:Rule
    val dbRule = InMemoryDatabaseRule(seedSystemCategories = true)

    private val dao get() = dbRule.db.categoryDao()
    private val repo by lazy { CategoryRepositoryImpl(dbRule.db) }
    private val runner by lazy { RoomTransactionRunner(dbRule.db) }
    private val clock = Clock.fixed(Instant.parse("2026-11-03T00:00:00Z"), ZoneOffset.UTC)

    @Test
    fun `delete with a live child returns HasChildren and writes nothing`() =
        runTest {
            dao.insert(category(id = "cat-1"))
            dao.insert(category(id = "cat-2", parentId = "cat-1"))
            val before = checkNotNull(dao.getById("cat-1"))

            val result = DeleteCategoryUseCase(repo, runner, clock)("cat-1")

            assertEquals(Outcome.Err(CategoryError.HasChildren), result)
            assertEquals(before, dao.getById("cat-1"))
            assertNull(dao.getById("cat-1")?.deletedAt)
        }

    @Test
    fun `update into an invalid parent leaves the row unchanged`() =
        runTest {
            dao.insert(category(id = "cat-1"))
            val before = checkNotNull(dao.getById("cat-1"))

            val result =
                UpdateCategoryUseCase(repo, runner, clock)(
                    before.toDomain().copy(name = "Renamed", parentId = "ghost"),
                )

            assertEquals(Outcome.Err(CategoryError.InvalidParent), result)
            assertEquals(before, dao.getById("cat-1"))
        }

    @Test
    fun `create then read back`() =
        runTest {
            val result = CreateCategoryUseCase(repo, runner, clock)(category(id = "new", name = "Fresh").toDomain())

            assertTrue(result is Outcome.Ok)
            val id = (result as Outcome.Ok).value
            val stored = dao.getById(id)
            assertNotNull(stored)
            assertEquals("Fresh", stored?.name)
            assertNull(stored?.deletedAt)
        }
}
