package io.github.toanhp123.finote.domain.category.usecase

import io.github.toanhp123.finote.domain.category.model.DeleteImpact
import io.github.toanhp123.finote.domain.category.repository.CategoryRepository
import javax.inject.Inject

class CountCategoryTransactionsUseCase
    @Inject
    constructor(
        private val repository: CategoryRepository,
    ) {
        /** Live transactions, rules and budgets of the category and its live children: what a delete would move. */
        suspend operator fun invoke(id: String): DeleteImpact {
            val category = repository.getCategory(id) ?: return DeleteImpact(transactions = 0, rules = 0, budgets = 0)
            val ids = repository.getLevel(category.type, id).mapTo(mutableSetOf(id)) { it.id }
            return DeleteImpact(
                transactions = repository.countLiveTransactions(ids),
                rules = repository.countRules(ids),
                budgets = repository.countBudgets(ids),
            )
        }
    }
