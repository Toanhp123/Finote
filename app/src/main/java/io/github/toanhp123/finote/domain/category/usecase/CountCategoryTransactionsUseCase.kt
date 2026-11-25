package io.github.toanhp123.finote.domain.category.usecase

import io.github.toanhp123.finote.domain.category.repository.CategoryRepository
import javax.inject.Inject

class CountCategoryTransactionsUseCase
    @Inject
    constructor(
        private val repository: CategoryRepository,
    ) {
        /** Live transactions of the category and of its live children: what a delete would move. */
        suspend operator fun invoke(id: String): Int {
            val category = repository.getCategory(id) ?: return 0
            val ids = repository.getLevel(category.type, id).mapTo(mutableSetOf(id)) { it.id }
            return repository.countLiveTransactions(ids)
        }
    }
