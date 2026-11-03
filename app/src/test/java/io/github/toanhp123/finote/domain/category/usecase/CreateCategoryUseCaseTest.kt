package io.github.toanhp123.finote.domain.category.usecase

import io.github.toanhp123.finote.domain.category.model.Category
import io.github.toanhp123.finote.domain.category.testCategory
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class CreateCategoryUseCaseTest {
    private val now = Instant.parse("2026-11-03T00:00:00Z")
    private val repo = FakeCategoryRepository()
    private val create = CreateCategoryUseCase(repo, ValidateCategoryUseCase(), Clock.fixed(now, ZoneOffset.UTC))

    private suspend fun assertRejected(category: Category) {
        val failure = runCatching { create(category) }.exceptionOrNull()
        assertTrue(failure.toString(), failure is IllegalArgumentException)
        assertEquals(0, repo.writes)
    }

    @Test
    fun `blank name is rejected`() =
        runTest {
            assertRejected(testCategory(name = ""))
            assertRejected(testCategory(name = "   "))
        }

    @Test
    fun `valid category gets a new uuid, clock timestamps and no deletedAt`() =
        runTest {
            val id = create(testCategory().copy(createdAt = 1L, updatedAt = 2L, deletedAt = 7L))

            UUID.fromString(id)
            assertNotEquals("cat-1", id)
            val saved = repo.stored.getValue(id)
            assertEquals(id, saved.id)
            assertEquals(now.toEpochMilli(), saved.createdAt)
            assertEquals(now.toEpochMilli(), saved.updatedAt)
            assertNull(saved.deletedAt)
            assertEquals(1, repo.writes)
        }
}
