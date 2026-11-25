package io.github.toanhp123.finote.domain.category.usecase

import io.github.toanhp123.finote.domain.category.model.KeywordRule
import io.github.toanhp123.finote.domain.category.repository.CategoryRepository
import io.github.toanhp123.finote.domain.category.repository.KeywordRuleRepository
import io.github.toanhp123.finote.domain.category.service.matchRows
import io.github.toanhp123.finote.domain.common.Outcome
import io.github.toanhp123.finote.domain.common.TransactionRunner
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class ApplyRuleToPastUseCase
    @Inject
    constructor(
        private val rules: KeywordRuleRepository,
        private val categories: CategoryRepository,
        private val tx: TransactionRunner,
    ) {
        /**
         * Sets the category (and the rule's merchant name, if it has one) on every past uncategorized notification row
         * that [rule] alone matches, and returns how many it wrote. A hidden or deleted category writes nothing.
         * `updatedAt` stays: this is a guess, not an edit by the user.
         */
        suspend operator fun invoke(rule: KeywordRule): Int {
            var written = 0
            tx.write<Unit, Nothing> {
                val live = categories.observeCategories().first().associateBy { it.id }
                val matches = matchRows(rule, rules.getUncategorizedNotificationRows(), live)
                rules.setCategory(matches)
                written = matches.size
                Outcome.Ok(Unit)
            }
            return written
        }
    }
