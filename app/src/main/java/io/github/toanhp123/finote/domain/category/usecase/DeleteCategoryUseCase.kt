package io.github.toanhp123.finote.domain.category.usecase

import io.github.toanhp123.finote.domain.category.error.CategoryError
import io.github.toanhp123.finote.domain.category.repository.CategoryRepository
import io.github.toanhp123.finote.domain.category.service.CategoryRules
import io.github.toanhp123.finote.domain.common.Outcome
import io.github.toanhp123.finote.domain.common.TransactionRunner
import java.time.Clock
import javax.inject.Inject

class DeleteCategoryUseCase
    @Inject
    constructor(
        private val repository: CategoryRepository,
        private val tx: TransactionRunner,
        private val clock: Clock,
    ) {
        /** Soft delete. An unknown id is `NotFound`; deleting an already deleted category does nothing. */
        suspend operator fun invoke(id: String): Outcome<Unit, CategoryError> =
            tx.write {
                val old = repository.getCategory(id)
                when {
                    old == null -> {
                        Outcome.Err(CategoryError.NotFound)
                    }

                    old.deletedAt != null -> {
                        Outcome.Ok(Unit)
                    }

                    else -> {
                        val error = CategoryRules.checkDelete(old, repository.countLiveChildren(id))
                        if (error != null) {
                            Outcome.Err(error)
                        } else {
                            repository.softDelete(id, clock.millis())
                            Outcome.Ok(Unit)
                        }
                    }
                }
            }
    }
