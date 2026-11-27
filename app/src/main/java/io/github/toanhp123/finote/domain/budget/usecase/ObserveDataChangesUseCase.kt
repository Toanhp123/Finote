package io.github.toanhp123.finote.domain.budget.usecase

import io.github.toanhp123.finote.domain.budget.repository.BudgetRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** Emits once at start, then after each commit to the tables a budget alert depends on. */
class ObserveDataChangesUseCase
    @Inject
    constructor(
        private val budgets: BudgetRepository,
    ) {
        operator fun invoke(): Flow<Unit> = budgets.observeChanges()
    }
