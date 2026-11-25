package io.github.toanhp123.finote.domain.category.usecase

import io.github.toanhp123.finote.domain.category.error.CategoryError
import io.github.toanhp123.finote.domain.category.model.MoveTarget
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
        /**
         * Soft-deletes the category and its live children, and moves every transaction of the branch to [target]. An
         * unknown id is `NotFound`; deleting an already deleted category does nothing.
         */
        suspend operator fun invoke(
            id: String,
            target: MoveTarget,
        ): Outcome<Unit, CategoryError> =
            tx.write {
                val old = repository.getCategory(id)
                if (old == null) {
                    Outcome.Err(CategoryError.NotFound)
                } else if (old.deletedAt != null) {
                    Outcome.Ok(Unit)
                } else {
                    val children = repository.getLevel(old.type, old.id)
                    val branchIds = children.mapTo(mutableSetOf(old.id)) { it.id }
                    val targetId = (target as? MoveTarget.Into)?.id
                    val error =
                        (listOf(old) + children).firstNotNullOfOrNull { CategoryRules.checkDelete(it) }
                            ?: targetId?.let {
                                CategoryRules.checkMoveTarget(
                                    old,
                                    repository.getCategory(it),
                                    branchIds,
                                )
                            }
                    if (error != null) {
                        Outcome.Err(error)
                    } else {
                        repository.deleteAndMove(branchIds, targetId, clock.millis())
                        Outcome.Ok(Unit)
                    }
                }
            }
    }
