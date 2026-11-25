package io.github.toanhp123.finote.domain.category.usecase

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.toanhp123.finote.data.category.mapper.toDomain
import io.github.toanhp123.finote.data.category.repository.CategoryRepositoryImpl
import io.github.toanhp123.finote.data.database.InMemoryDatabaseRule
import io.github.toanhp123.finote.data.database.RoomTransactionRunner
import io.github.toanhp123.finote.data.database.account
import io.github.toanhp123.finote.data.database.category
import io.github.toanhp123.finote.data.database.transaction
import io.github.toanhp123.finote.domain.category.error.CategoryError
import io.github.toanhp123.finote.domain.category.model.MoveTarget
import io.github.toanhp123.finote.domain.category.repository.CategoryRepository
import io.github.toanhp123.finote.domain.common.Outcome
import io.github.toanhp123.finote.domain.common.TransactionRunner
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
    private val roomRunner by lazy { RoomTransactionRunner(dbRule.db) }
    private val clock = Clock.fixed(Instant.parse("2026-11-03T00:00:00Z"), ZoneOffset.UTC)

    private var writes = 0
    private val runner =
        object : TransactionRunner {
            override suspend fun <T, E> write(block: suspend () -> Outcome<T, E>): Outcome<T, E> {
                writes++
                return roomRunner.write(block)
            }
        }

    private suspend fun seedBranch() {
        dao.insert(category(id = "cat-1"))
        dao.insert(category(id = "cat-2", parentId = "cat-1"))
        dao.insert(category(id = "cat-3"))
        dbRule.db.accountDao().insert(account())
        dbRule.db.transactionDao().insert(transaction(id = "tx-1", categoryId = "cat-1"))
        dbRule.db.transactionDao().insert(transaction(id = "tx-2", categoryId = "cat-2"))
    }

    private suspend fun txCategory(id: String) =
        dbRule.db
            .transactionDao()
            .getById(id)
            ?.categoryId

    @Test
    fun `delete of a branch runs in one write and moves its transactions`() =
        runTest {
            seedBranch()

            val result = DeleteCategoryUseCase(repo, runner, clock)("cat-1", MoveTarget.Into("cat-3"))

            assertEquals(Outcome.Ok(Unit), result)
            assertEquals(1, writes)
            assertNotNull(dao.getById("cat-1")?.deletedAt)
            assertNotNull(dao.getById("cat-2")?.deletedAt)
            assertNull(dao.getById("cat-3")?.deletedAt)
            assertEquals("cat-3", txCategory("tx-1"))
            assertEquals("cat-3", txCategory("tx-2"))
        }

    @Test
    fun `delete that fails after the move rolls the whole branch back`() =
        runTest {
            seedBranch()
            val failing =
                object : CategoryRepository by repo {
                    override suspend fun deleteAndMove(
                        categoryIds: Set<String>,
                        targetId: String?,
                        deletedAt: Long,
                    ) {
                        repo.deleteAndMove(categoryIds, targetId, deletedAt)
                        error("boom")
                    }
                }

            val thrown =
                runCatching { DeleteCategoryUseCase(failing, runner, clock)("cat-1", MoveTarget.Into("cat-3")) }
                    .exceptionOrNull()

            assertEquals("boom", thrown?.message)
            assertEquals(1, writes)
            assertNull(dao.getById("cat-1")?.deletedAt)
            assertNull(dao.getById("cat-2")?.deletedAt)
            assertEquals("cat-1", txCategory("tx-1"))
            assertEquals("cat-2", txCategory("tx-2"))
        }

    @Test
    fun `delete with a target inside the branch writes nothing`() =
        runTest {
            seedBranch()

            val result = DeleteCategoryUseCase(repo, runner, clock)("cat-1", MoveTarget.Into("cat-2"))

            assertEquals(Outcome.Err(CategoryError.InvalidMoveTarget), result)
            assertNull(dao.getById("cat-1")?.deletedAt)
            assertEquals("cat-1", txCategory("tx-1"))
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
