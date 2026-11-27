package io.github.toanhp123.finote.domain.budget.usecase

import io.github.toanhp123.finote.domain.budget.error.BudgetError
import io.github.toanhp123.finote.domain.budget.repository.BudgetRepository
import io.github.toanhp123.finote.domain.common.Outcome
import io.github.toanhp123.finote.domain.common.TransactionRunner
import java.time.Clock
import java.time.YearMonth
import javax.inject.Inject

class DeleteBudgetUseCase
    @Inject
    constructor(
        private val budgets: BudgetRepository,
        private val tx: TransactionRunner,
        private val clock: Clock,
    ) {
        /** Hard-deletes a row of the current month. The month stays set up, so it is not copied again. */
        suspend operator fun invoke(id: String): Outcome<Unit, BudgetError> =
            tx.write {
                val row = budgets.get(id)
                when {
                    row == null -> {
                        Outcome.Err(BudgetError.NotFound)
                    }

                    row.month != YearMonth.now(clock) -> {
                        Outcome.Err(BudgetError.NotCurrentMonth)
                    }

                    else -> {
                        budgets.delete(id)
                        Outcome.Ok(Unit)
                    }
                }
            }
    }
