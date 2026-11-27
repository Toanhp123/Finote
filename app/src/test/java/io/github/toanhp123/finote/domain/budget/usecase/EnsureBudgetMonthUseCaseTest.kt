package io.github.toanhp123.finote.domain.budget.usecase

import io.github.toanhp123.finote.domain.budget.FakeBudgetRepository
import io.github.toanhp123.finote.domain.budget.testBudget
import io.github.toanhp123.finote.domain.common.FakeTransactionRunner
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneOffset

class EnsureBudgetMonthUseCaseTest {
    private val sep = YearMonth.of(2026, 9)
    private val oct = YearMonth.of(2026, 10)
    private val nov = YearMonth.of(2026, 11)
    private val dec = YearMonth.of(2026, 12)
    private val repo = FakeBudgetRepository()
    private val clock = Clock.fixed(Instant.parse("2026-12-05T00:00:00Z"), ZoneOffset.UTC)
    private val ensure = EnsureBudgetMonthUseCase(repo, FakeTransactionRunner(), clock)

    @Test
    fun `copies the latest set-up month and marks it`() =
        runTest {
            repo.stored["a"] = testBudget("a", oct, "food", 3_000_000, isRollover = true, threshold = 90, mark = 100)
            repo.stored["t"] = testBudget("t", oct, null, 9_000_000)
            repo.setUp += oct

            ensure(nov)

            val copied = repo.getMonth(nov)
            assertEquals(2, copied.size)
            assertTrue(copied.none { it.id == "a" || it.id == "t" })
            val food = copied.single { it.categoryId == "food" }
            assertEquals(3_000_000L, food.limitAmount)
            assertTrue(food.isRollover)
            assertEquals(90, food.alertThreshold)
            assertNull(food.lastAlertSentThreshold)
            assertEquals(clock.millis(), food.createdAt)
            assertEquals(9_000_000L, copied.single { it.categoryId == null }.limitAmount)
            assertTrue(repo.isSetUp(nov))
        }

    @Test
    fun `fills a skipped month`() =
        runTest {
            repo.stored["a"] = testBudget("a", oct, "food")
            repo.setUp += oct

            ensure(dec)

            assertEquals(listOf("food"), repo.getMonth(nov).map { it.categoryId })
            assertEquals(listOf("food"), repo.getMonth(dec).map { it.categoryId })
            assertNotEquals(repo.getMonth(nov).single().id, repo.getMonth(dec).single().id)
            assertTrue(repo.isSetUp(nov) && repo.isSetUp(dec))
        }

    @Test
    fun `copies nothing when the user deleted every budget last month`() =
        runTest {
            repo.stored["s"] = testBudget("s", sep, "food")
            repo.setUp += sep
            repo.setUp += oct

            ensure(nov)

            assertTrue(repo.getMonth(nov).isEmpty())
            assertTrue(repo.isSetUp(nov))
        }

    @Test
    fun `nothing is marked when no month was ever set up`() =
        runTest {
            ensure(nov)

            assertTrue(repo.setUp.isEmpty())
        }

    @Test
    fun `second call copies nothing`() =
        runTest {
            repo.stored["a"] = testBudget("a", oct, "food")
            repo.setUp += oct
            ensure(nov)
            val first = repo.getMonth(nov)

            ensure(nov)

            assertEquals(first, repo.getMonth(nov))
        }
}
