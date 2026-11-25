package io.github.toanhp123.finote.domain.category.usecase

import io.github.toanhp123.finote.domain.category.model.CategoryType
import io.github.toanhp123.finote.domain.category.model.KeywordRule
import io.github.toanhp123.finote.domain.category.repository.CategoryRepository
import io.github.toanhp123.finote.domain.category.repository.KeywordRuleRepository
import javax.inject.Inject

class FindRenamingRuleUseCase
    @Inject
    constructor(
        private val rules: KeywordRuleRepository,
        private val categories: CategoryRepository,
    ) {
        /**
         * The active rule whose short name is [merchantName] and whose category is of [type]: the rule that renamed a
         * booked row. The one with [startCategoryId] wins, since that is the category the row was booked with.
         */
        suspend operator fun invoke(
            merchantName: String,
            type: CategoryType,
            startCategoryId: String?,
        ): KeywordRule? {
            val named =
                rules.getRules().filter {
                    it.isActive &&
                        it.merchantName == merchantName &&
                        categories.getCategory(it.categoryId)?.type == type
                }
            return named.firstOrNull { it.categoryId == startCategoryId } ?: named.firstOrNull()
        }
    }
