package io.github.toanhp123.finote.domain.category.usecase

import io.github.toanhp123.finote.domain.category.model.KeywordRule
import io.github.toanhp123.finote.domain.category.repository.KeywordRuleRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveKeywordRulesUseCase
    @Inject
    constructor(
        private val rules: KeywordRuleRepository,
    ) {
        operator fun invoke(): Flow<List<KeywordRule>> = rules.observeRules()
    }
