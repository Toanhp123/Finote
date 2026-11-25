package io.github.toanhp123.finote.domain.category.usecase

import io.github.toanhp123.finote.domain.category.model.CategoryType
import io.github.toanhp123.finote.domain.category.model.KeywordRule
import io.github.toanhp123.finote.domain.category.repository.CategoryRepository
import io.github.toanhp123.finote.domain.category.repository.KeywordRuleRepository
import io.github.toanhp123.finote.domain.common.normalizeKeyword
import javax.inject.Inject

class FindUserKeywordRuleUseCase
    @Inject
    constructor(
        private val rules: KeywordRuleRepository,
        private val categories: CategoryRepository,
    ) {
        /** The user rule with this keyword (compared normalized) and a category of [type]; null when none. */
        suspend operator fun invoke(
            keyword: String,
            type: CategoryType,
        ): KeywordRule? {
            val normalized = normalizeKeyword(keyword)
            return rules.getRules().firstOrNull {
                it.isUserDefined &&
                    it.normalizedKeyword == normalized &&
                    categories.getCategory(it.categoryId)?.type == type
            }
        }
    }
