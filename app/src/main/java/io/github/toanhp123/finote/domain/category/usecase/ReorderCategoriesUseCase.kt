package io.github.toanhp123.finote.domain.category.usecase

import io.github.toanhp123.finote.domain.category.error.CategoryError
import io.github.toanhp123.finote.domain.category.model.CategoryType
import io.github.toanhp123.finote.domain.category.repository.CategoryRepository
import io.github.toanhp123.finote.domain.common.Outcome
import io.github.toanhp123.finote.domain.common.TransactionRunner
import java.time.Clock
import javax.inject.Inject

class ReorderCategoriesUseCase
    @Inject
    constructor(
        private val repository: CategoryRepository,
        private val tx: TransactionRunner,
        private val clock: Clock,
    ) {
        /**
         * Gives the rows of one level the order of [orderedIds]; rows of the level that are not listed (hidden ones)
         * follow in their old order, so each row ends with its own `sortOrder`. Ids outside the level are ignored.
         */
        suspend operator fun invoke(
            type: CategoryType,
            parentId: String?,
            orderedIds: List<String>,
        ): Outcome<Unit, CategoryError> =
            tx.write {
                val level = repository.getLevel(type, parentId).map { it.id }
                val listed = orderedIds.filter { it in level }.distinct()
                repository.reorder(listed + (level - listed.toSet()), clock.millis())
                Outcome.Ok(Unit)
            }
    }
