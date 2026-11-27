package io.github.toanhp123.finote.data.budget.repository

import io.github.toanhp123.finote.data.budget.local.entity.BudgetMonthEntity
import io.github.toanhp123.finote.data.budget.mapper.toDomain
import io.github.toanhp123.finote.data.budget.mapper.toEntity
import io.github.toanhp123.finote.data.database.FinoteDatabase
import io.github.toanhp123.finote.data.database.mapStorageErrors
import io.github.toanhp123.finote.data.database.storage
import io.github.toanhp123.finote.data.database.storageWrite
import io.github.toanhp123.finote.domain.budget.model.Budget
import io.github.toanhp123.finote.domain.budget.repository.BudgetRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.YearMonth
import javax.inject.Inject

class BudgetRepositoryImpl
    @Inject
    constructor(
        private val db: FinoteDatabase,
    ) : BudgetRepository {
        private val budgets = db.budgetDao()

        override suspend fun getMonth(month: YearMonth): List<Budget> =
            storage { budgets.getByMonth(month.toString()).map { it.toDomain() } }

        override fun observeUpTo(month: YearMonth): Flow<List<Budget>> =
            budgets.observeUpTo(month.toString()).map { list -> list.map { it.toDomain() } }.mapStorageErrors()

        override fun observeFirstSetUp(): Flow<YearMonth?> =
            budgets.observeFirstSetUp().map { it?.let(YearMonth::parse) }.mapStorageErrors()

        override suspend fun get(id: String): Budget? = storage { budgets.getById(id)?.toDomain() }

        override suspend fun insert(budget: Budget) {
            db.storageWrite { budgets.insert(budget.toEntity()) }
        }

        override suspend fun update(budget: Budget) {
            db.storageWrite { budgets.update(budget.toEntity()) }
        }

        override suspend fun delete(id: String) {
            db.storageWrite { budgets.deleteById(id) }
        }

        override suspend fun isSetUp(month: YearMonth): Boolean = storage { budgets.isSetUp(month.toString()) }

        override suspend fun latestSetUpBefore(month: YearMonth): YearMonth? =
            storage { budgets.latestSetUpBefore(month.toString())?.let(YearMonth::parse) }

        override suspend fun markSetUp(month: YearMonth) {
            db.storageWrite { budgets.markSetUp(BudgetMonthEntity(month.toString())) }
        }

        override suspend fun hasAny(): Boolean = storage { budgets.hasAny() }

        // The first emission is the tracker's own kick-start: a watcher gets one check at start.
        override fun observeChanges(): Flow<Unit> =
            db.invalidationTracker
                .createFlow("transactions", "budgets", "categories", "accounts")
                .map { }
                .mapStorageErrors()
    }
