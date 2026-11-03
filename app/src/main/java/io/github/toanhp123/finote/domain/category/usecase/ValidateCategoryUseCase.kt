package io.github.toanhp123.finote.domain.category.usecase

import io.github.toanhp123.finote.domain.category.model.Category
import javax.inject.Inject

/** Checks a category before it is written. Throws [IllegalArgumentException] with a reason. */
class ValidateCategoryUseCase
    @Inject
    constructor() {
        operator fun invoke(category: Category) {
            require(category.name.isNotBlank()) { "name must not be blank" }
        }
    }
