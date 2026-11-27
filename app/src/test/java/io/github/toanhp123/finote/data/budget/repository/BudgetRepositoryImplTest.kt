package io.github.toanhp123.finote.data.budget.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import io.github.toanhp123.finote.data.database.InMemoryDatabaseRule
import io.github.toanhp123.finote.data.database.account
import io.github.toanhp123.finote.data.database.transaction
import io.github.toanhp123.finote.domain.budget.model.Budget
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.YearMonth

@RunWith(AndroidJUnit4::class)
class BudgetRepositoryImplTest {
    @get:Rule
    val dbRule = InMemoryDatabaseRule()

    private val repository by lazy { BudgetRepositoryImpl(dbRule.db) }

    private val nov = YearMonth.of(2026, 11)

    private val total =
        Budget("b-1", null, 3_000_000L, nov, isRollover = true, 90, 80, createdAt = 5L, updatedAt = 6L)

    @Test
    fun `insert and get round trip`() =
        runTest {
            repository.insert(total)

            assertEquals(total, repository.get("b-1"))
            assertEquals(listOf(total), repository.getMonth(nov))
            assertTrue(repository.hasAny())
            assertNull(repository.get("missing"))
        }

    @Test
    fun `months are set up and found by the latest one before`() =
        runTest {
            repository.markSetUp(YearMonth.of(2026, 9))
            repository.markSetUp(YearMonth.of(2026, 10))
            repository.markSetUp(YearMonth.of(2026, 10))

            assertTrue(repository.isSetUp(YearMonth.of(2026, 10)))
            assertFalse(repository.isSetUp(nov))
            assertEquals(YearMonth.of(2026, 10), repository.latestSetUpBefore(nov))
            assertNull(repository.latestSetUpBefore(YearMonth.of(2026, 9)))
            assertFalse(repository.hasAny())
        }

    @Test
    fun `observeChanges emits after a transaction insert`() =
        runTest {
            dbRule.db.accountDao().insert(account())

            repository.observeChanges().test {
                awaitItem() // the tracker's first emission
                dbRule.db.transactionDao().insert(transaction())
                awaitItem()
                cancelAndIgnoreRemainingEvents()
            }
        }
}
