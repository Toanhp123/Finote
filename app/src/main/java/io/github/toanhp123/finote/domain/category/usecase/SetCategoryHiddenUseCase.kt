package io.github.toanhp123.finote.domain.category.usecase

import io.github.toanhp123.finote.domain.category.repository.CategoryRepository
import java.time.Clock
import javax.inject.Inject

class SetCategoryHiddenUseCase
    @Inject
    constructor(
        private val repository: CategoryRepository,
        private val clock: Clock,
    ) {
        /** Hides or shows a category. The repository rejects a missing or deleted one. */
        suspend operator fun invoke(
            id: String,
            hidden: Boolean,
        ) {
            repository.setHidden(id, hidden, clock.millis())
        }
    }
