package io.github.toanhp123.finote.domain.category.usecase

import io.github.toanhp123.finote.domain.category.error.CategoryError
import io.github.toanhp123.finote.domain.category.model.Category
import io.github.toanhp123.finote.domain.category.repository.CategoryRepository
import io.github.toanhp123.finote.domain.category.service.CategoryRules
import io.github.toanhp123.finote.domain.common.Outcome
import io.github.toanhp123.finote.domain.common.TransactionRunner
import java.time.Clock
import java.util.UUID
import javax.inject.Inject

class CreateCategoryUseCase
    @Inject
    constructor(
        private val repository: CategoryRepository,
        private val tx: TransactionRunner,
        private val clock: Clock,
    ) {
        /**
         * Assigns a new id, stamps both timestamps from the clock and clears `deletedAt`. Returns the new id,
         * or the rule that refused the category.
         */
        suspend operator fun invoke(category: Category): Outcome<String, CategoryError> {
            val now = clock.millis()
            val fresh =
                category.copy(
                    id = UUID.randomUUID().toString(),
                    createdAt = now,
                    updatedAt = now,
                    deletedAt = null,
                )
            return tx.write {
                val parent = fresh.parentId?.let { repository.getCategory(it) }
                val error = CategoryRules.checkNew(fresh, parent)
                if (error != null) {
                    Outcome.Err(error)
                } else {
                    repository.add(fresh)
                    Outcome.Ok(fresh.id)
                }
            }
        }
    }
