package io.github.toanhp123.finote.domain.category.usecase

import io.github.toanhp123.finote.domain.category.repository.KeywordRuleRepository
import java.time.Clock
import javax.inject.Inject

class SetSystemRuleActiveUseCase
    @Inject
    constructor(
        private val rules: KeywordRuleRepository,
        private val clock: Clock,
    ) {
        /** Switches a system rule on or off. A user rule or an unknown id is left alone. */
        suspend operator fun invoke(
            id: String,
            active: Boolean,
        ) {
            val rule = rules.getRule(id) ?: return
            if (!rule.isUserDefined) rules.setActive(id, active, clock.millis())
        }
    }
