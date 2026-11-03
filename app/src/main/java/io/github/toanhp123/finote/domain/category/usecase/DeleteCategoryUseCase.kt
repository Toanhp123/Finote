package io.github.toanhp123.finote.domain.category.usecase

import io.github.toanhp123.finote.domain.category.repository.CategoryRepository
import java.time.Clock
import javax.inject.Inject

class DeleteCategoryUseCase
    @Inject
    constructor(
        private val repository: CategoryRepository,
        private val clock: Clock,
    ) {
        /** Soft delete. The repository rejects a system category and one with live children. */
        suspend operator fun invoke(id: String) {
            repository.softDelete(id, clock.millis())
        }
    }
