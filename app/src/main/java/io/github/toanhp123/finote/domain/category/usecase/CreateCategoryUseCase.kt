package io.github.toanhp123.finote.domain.category.usecase

import io.github.toanhp123.finote.domain.category.model.Category
import io.github.toanhp123.finote.domain.category.repository.CategoryRepository
import java.time.Clock
import java.util.UUID
import javax.inject.Inject

class CreateCategoryUseCase
    @Inject
    constructor(
        private val repository: CategoryRepository,
        private val validate: ValidateCategoryUseCase,
        private val clock: Clock,
    ) {
        /** Assigns a new id, stamps both timestamps from the clock and clears `deletedAt`. Returns the new id. */
        suspend operator fun invoke(category: Category): String {
            val now = clock.millis()
            val fresh =
                category.copy(
                    id = UUID.randomUUID().toString(),
                    createdAt = now,
                    updatedAt = now,
                    deletedAt = null,
                )
            validate(fresh)
            repository.add(fresh)
            return fresh.id
        }
    }
