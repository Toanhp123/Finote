package io.github.toanhp123.finote.domain.category.usecase

import io.github.toanhp123.finote.domain.category.model.Category
import io.github.toanhp123.finote.domain.category.repository.CategoryRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveCategoriesUseCase
    @Inject
    constructor(
        private val repository: CategoryRepository,
    ) {
        operator fun invoke(): Flow<List<Category>> = repository.observeCategories()
    }
