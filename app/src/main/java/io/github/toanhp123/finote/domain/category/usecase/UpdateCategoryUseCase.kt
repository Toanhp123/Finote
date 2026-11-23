package io.github.toanhp123.finote.domain.category.usecase

import io.github.toanhp123.finote.domain.category.error.CategoryError
import io.github.toanhp123.finote.domain.category.model.Category
import io.github.toanhp123.finote.domain.category.repository.CategoryRepository
import io.github.toanhp123.finote.domain.category.service.CategoryRules
import io.github.toanhp123.finote.domain.common.Outcome
import io.github.toanhp123.finote.domain.common.TransactionRunner
import java.time.Clock
import javax.inject.Inject

class UpdateCategoryUseCase
    @Inject
    constructor(
        private val repository: CategoryRepository,
        private val tx: TransactionRunner,
        private val clock: Clock,
    ) {
        /** Stamps `updatedAt`. Pass the stored `name` unless the user edited it: see [CategoryRepository.update]. */
        suspend operator fun invoke(category: Category): Outcome<Unit, CategoryError> {
            val updated = category.copy(updatedAt = clock.millis())
            return tx.write {
                val old = repository.getCategory(updated.id)?.takeIf { it.deletedAt == null }
                if (old == null) {
                    Outcome.Err(CategoryError.NotFound)
                } else {
                    val parent = updated.parentId?.let { repository.getCategory(it) }
                    val liveChildren = repository.countLiveChildren(updated.id)
                    val error = CategoryRules.checkUpdate(old, updated, parent, liveChildren)
                    if (error != null) {
                        Outcome.Err(error)
                    } else {
                        repository.update(updated)
                        Outcome.Ok(Unit)
                    }
                }
            }
        }
    }
