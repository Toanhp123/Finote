package io.github.toanhp123.finote.domain.budget.repository

import io.github.toanhp123.finote.domain.budget.model.Budget
import kotlinx.coroutines.flow.Flow
import java.time.YearMonth

/**
 * Budget rows and the months marked as set up. Rows are hard-deleted. The repository checks no business rule:
 * use cases decide inside `TransactionRunner.write` before they call it.
 */
interface BudgetRepository {
    suspend fun getMonth(month: YearMonth): List<Budget>

    /** Rows of [month] and every earlier month, oldest first. */
    fun observeUpTo(month: YearMonth): Flow<List<Budget>>

    /** The earliest set-up month, or null while no month is set up. */
    fun observeFirstSetUp(): Flow<YearMonth?>

    suspend fun get(id: String): Budget?

    suspend fun insert(budget: Budget)

    suspend fun update(budget: Budget)

    suspend fun delete(id: String)

    suspend fun isSetUp(month: YearMonth): Boolean

    /** The latest set-up month before [month], or null. */
    suspend fun latestSetUpBefore(month: YearMonth): YearMonth?

    /** Idempotent. */
    suspend fun markSetUp(month: YearMonth)

    /** True when any budget row exists, in any month. */
    suspend fun hasAny(): Boolean

    /**
     * Emits once at start, then after each commit to `transactions`, `budgets`, `categories` or `accounts`.
     * Bursts may be conflated into one emission.
     */
    fun observeChanges(): Flow<Unit>
}
