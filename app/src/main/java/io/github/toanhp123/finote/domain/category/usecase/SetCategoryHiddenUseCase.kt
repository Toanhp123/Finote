package io.github.toanhp123.finote.domain.category.usecase

import io.github.toanhp123.finote.domain.category.error.CategoryError
import io.github.toanhp123.finote.domain.category.repository.CategoryRepository
import io.github.toanhp123.finote.domain.common.Outcome
import io.github.toanhp123.finote.domain.common.TransactionRunner
import java.time.Clock
import javax.inject.Inject

class SetCategoryHiddenUseCase
    @Inject
    constructor(
        private val repository: CategoryRepository,
        private val tx: TransactionRunner,
        private val clock: Clock,
    ) {
        /** Hides or shows a category. A missing or deleted one is `NotFound`. */
        suspend operator fun invoke(
            id: String,
            hidden: Boolean,
        ): Outcome<Unit, CategoryError> =
            tx.write {
                val old = repository.getCategory(id)
                if (old == null || old.deletedAt != null) {
                    Outcome.Err(CategoryError.NotFound)
                } else {
                    repository.setHidden(id, hidden, clock.millis())
                    Outcome.Ok(Unit)
                }
            }
    }
