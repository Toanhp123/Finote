package io.github.toanhp123.finote.domain.category.usecase

import io.github.toanhp123.finote.domain.category.model.KeywordRule
import io.github.toanhp123.finote.domain.category.repository.CategoryRepository
import io.github.toanhp123.finote.domain.category.repository.KeywordRuleRepository
import io.github.toanhp123.finote.domain.category.service.matchRows
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class CountRuleMatchesUseCase
    @Inject
    constructor(
        private val rules: KeywordRuleRepository,
        private val categories: CategoryRepository,
    ) {
        /** How many past uncategorized notification rows [rule] would set: what [ApplyRuleToPastUseCase] writes. */
        suspend operator fun invoke(rule: KeywordRule): Int =
            matchRows(
                rule,
                rules.getUncategorizedNotificationRows(),
                categories.observeCategories().first().associateBy { it.id },
            ).size
    }
