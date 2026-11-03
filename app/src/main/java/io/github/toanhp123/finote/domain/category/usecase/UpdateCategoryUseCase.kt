package io.github.toanhp123.finote.domain.category.usecase

import io.github.toanhp123.finote.domain.category.model.Category
import io.github.toanhp123.finote.domain.category.repository.CategoryRepository
import java.time.Clock
import javax.inject.Inject

class UpdateCategoryUseCase
    @Inject
    constructor(
        private val repository: CategoryRepository,
        private val validate: ValidateCategoryUseCase,
        private val clock: Clock,
    ) {
        /** Stamps `updatedAt`. Pass the stored `name` unless the user edited it: see [CategoryRepository.update]. */
        suspend operator fun invoke(category: Category) {
            val updated = category.copy(updatedAt = clock.millis())
            validate(updated)
            repository.update(updated)
        }
    }
