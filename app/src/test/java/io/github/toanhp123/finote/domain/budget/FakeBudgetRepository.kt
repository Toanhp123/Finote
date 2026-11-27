package io.github.toanhp123.finote.domain.budget

import io.github.toanhp123.finote.domain.budget.model.Budget
import io.github.toanhp123.finote.domain.budget.repository.BudgetRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.time.YearMonth

/**
 * Copies from the real repository: rows sort by month then id, and a second row with the same month and a non-null
 * category is refused. Like SQLite's unique index it lets two total rows of one month through.
 */
class FakeBudgetRepository : BudgetRepository {
    val stored = linkedMapOf<String, Budget>()
    val setUp = sortedSetOf<YearMonth>()
    private val version = MutableStateFlow(0)

    private fun changed() {
        version.value++
    }

    override suspend fun getMonth(month: YearMonth): List<Budget> =
        stored.values.filter { it.month == month }.sortedBy { it.id }

    override fun observeUpTo(month: YearMonth): Flow<List<Budget>> =
        version.map { _ ->
            stored.values.filter { it.month <= month }.sortedWith(compareBy<Budget> { it.month }.thenBy { it.id })
        }

    override fun observeFirstSetUp(): Flow<YearMonth?> = version.map { setUp.firstOrNull() }

    override suspend fun get(id: String): Budget? = stored[id]

    override suspend fun insert(budget: Budget) {
        val clash =
            budget.categoryId != null &&
                stored.values.any { it.month == budget.month && it.categoryId == budget.categoryId }
        check(!clash && budget.id !in stored) { "UNIQUE constraint failed" }
        stored[budget.id] = budget
        changed()
    }

    override suspend fun update(budget: Budget) {
        if (budget.id in stored) stored[budget.id] = budget
        changed()
    }

    override suspend fun delete(id: String) {
        stored.remove(id)
        changed()
    }

    override suspend fun isSetUp(month: YearMonth): Boolean = month in setUp

    override suspend fun latestSetUpBefore(month: YearMonth): YearMonth? = setUp.lastOrNull { it < month }

    override suspend fun markSetUp(month: YearMonth) {
        setUp += month
        changed()
    }

    override suspend fun hasAny(): Boolean = stored.isNotEmpty()

    override fun observeChanges(): Flow<Unit> = version.map { }
}
