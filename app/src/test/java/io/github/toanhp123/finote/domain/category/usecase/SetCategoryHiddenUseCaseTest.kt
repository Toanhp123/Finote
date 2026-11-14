package io.github.toanhp123.finote.domain.category.usecase

import io.github.toanhp123.finote.domain.category.error.CategoryError
import io.github.toanhp123.finote.domain.category.testCategory
import io.github.toanhp123.finote.domain.common.FakeTransactionRunner
import io.github.toanhp123.finote.domain.common.Outcome
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class SetCategoryHiddenUseCaseTest {
    private val now = Instant.parse("2026-11-03T00:00:00Z")
    private val repo = FakeCategoryRepository()
    private val runner = FakeTransactionRunner()
    private val setHidden = SetCategoryHiddenUseCase(repo, runner, Clock.fixed(now, ZoneOffset.UTC))

    @Test
    fun `hide and show stamp updatedAt from the clock`() =
        runTest {
            repo.stored["cat-1"] = testCategory()

            assertEquals(Outcome.Ok(Unit), setHidden("cat-1", hidden = true))
            assertEquals(true, repo.stored.getValue("cat-1").isHidden)
            assertEquals(now.toEpochMilli(), repo.stored.getValue("cat-1").updatedAt)

            assertEquals(Outcome.Ok(Unit), setHidden("cat-1", hidden = false))
            assertEquals(false, repo.stored.getValue("cat-1").isHidden)
            assertEquals(2, repo.writes)
            assertEquals(2, runner.writes)
        }

    @Test
    fun `missing or soft-deleted row is NotFound and nothing is written`() =
        runTest {
            repo.stored["gone"] = testCategory(id = "gone").copy(deletedAt = 5L)

            assertEquals(Outcome.Err(CategoryError.NotFound), setHidden("ghost", hidden = true))
            assertEquals(Outcome.Err(CategoryError.NotFound), setHidden("gone", hidden = true))
            assertEquals(0, repo.writes)
        }
}
