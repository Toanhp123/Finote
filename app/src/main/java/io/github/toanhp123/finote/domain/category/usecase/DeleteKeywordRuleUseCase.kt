package io.github.toanhp123.finote.domain.category.usecase

import io.github.toanhp123.finote.domain.category.repository.KeywordRuleRepository
import javax.inject.Inject

class DeleteKeywordRuleUseCase
    @Inject
    constructor(
        private val rules: KeywordRuleRepository,
    ) {
        /** Deletes a user rule for good. A system rule stays: switch it off instead. */
        suspend operator fun invoke(id: String) = rules.deleteUserRule(id)
    }
