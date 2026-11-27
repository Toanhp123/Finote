package io.github.toanhp123.finote.domain.budget.usecase

import io.github.toanhp123.finote.domain.budget.repository.BudgetRepository
import io.github.toanhp123.finote.domain.common.Outcome
import io.github.toanhp123.finote.domain.common.TransactionRunner
import java.time.Clock
import java.time.YearMonth
import java.util.UUID
import javax.inject.Inject

/** Copies forward: a month never set up gets the budgets of the latest set-up month before it. */
class EnsureBudgetMonthUseCase
    @Inject
    constructor(
        private val budgets: BudgetRepository,
        private val tx: TransactionRunner,
        private val clock: Clock,
    ) {
        /**
         * Every month from the one after the latest set-up month up to [month] gets the same rows, with new ids and
         * no alert mark, and is marked set up (also with no rows, so a month the user emptied stays empty). Nothing
         * happens when [month] is set up already or no earlier month is. Safe inside another `write`.
         */
        suspend operator fun invoke(month: YearMonth) {
            if (budgets.isSetUp(month)) return
            tx.write<Unit, Nothing> {
                copyForward(month)
                Outcome.Ok(Unit)
            }
        }

        private suspend fun copyForward(month: YearMonth) {
            // Checked again inside the write: two callers must not both copy.
            val source = budgets.latestSetUpBefore(month)?.takeUnless { budgets.isSetUp(month) } ?: return
            val rows = budgets.getMonth(source)
            val now = clock.millis()
            generateSequence(source.plusMonths(1)) { it.plusMonths(1) }.takeWhile { it <= month }.forEach { target ->
                rows.forEach {
                    budgets.insert(
                        it.copy(
                            id = UUID.randomUUID().toString(),
                            month = target,
                            lastAlertSentThreshold = null,
                            createdAt = now,
                            updatedAt = now,
                        ),
                    )
                }
                budgets.markSetUp(target)
            }
        }
    }
