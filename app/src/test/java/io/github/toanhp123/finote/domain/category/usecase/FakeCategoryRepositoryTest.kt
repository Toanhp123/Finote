package io.github.toanhp123.finote.domain.category.usecase

import io.github.toanhp123.finote.domain.category.testCategory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class FakeCategoryRepositoryTest {
    private val repository = FakeCategoryRepository()

    @Test
    fun `observe sorts by sortOrder then createdAt and drops deleted rows`() =
        runTest {
            val late = testCategory(id = "late").copy(sortOrder = 1, createdAt = 9)
            val early = testCategory(id = "early").copy(sortOrder = 1, createdAt = 2)
            val first = testCategory(id = "first").copy(sortOrder = 0, createdAt = 50)
            val gone = testCategory(id = "gone").copy(sortOrder = -1, deletedAt = 7)
            listOf(late, early, first, gone).forEach { repository.add(it) }

            assertEquals(listOf("first", "early", "late"), repository.observeCategories().first().map { it.id })
        }
}
