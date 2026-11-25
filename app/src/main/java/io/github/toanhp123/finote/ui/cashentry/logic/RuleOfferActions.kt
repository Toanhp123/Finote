package io.github.toanhp123.finote.ui.cashentry.logic

import io.github.toanhp123.finote.domain.category.usecase.ApplyRuleToPastUseCase
import io.github.toanhp123.finote.domain.category.usecase.CountRuleMatchesUseCase
import io.github.toanhp123.finote.domain.category.usecase.FindRenamingRuleUseCase
import io.github.toanhp123.finote.domain.category.usecase.FindUserKeywordRuleUseCase
import io.github.toanhp123.finote.domain.category.usecase.SaveKeywordRuleUseCase
import io.github.toanhp123.finote.domain.common.Outcome
import io.github.toanhp123.finote.ui.cashentry.CashEntryState
import javax.inject.Inject

/** The reads and writes of the rule step. Each returns how the sheet changes after it; the ViewModel applies it. */
class RuleOfferActions
    @Inject
    constructor(
        private val saveRule: SaveKeywordRuleUseCase,
        private val findUserRule: FindUserKeywordRuleUseCase,
        private val findRenamingRule: FindRenamingRuleUseCase,
        private val countMatches: CountRuleMatchesUseCase,
        private val applyRule: ApplyRuleToPastUseCase,
    ) {
        /**
         * Takes the keyword of the rule that renamed the row, when there is one, and marks whether that keyword is a
         * user rule of its type already.
         */
        internal suspend fun lookUp(offer: RuleOfferState): CashEntryState.() -> CashEntryState {
            val renamer = offer.renamedBy?.let { findRenamingRule(it.merchantName, offer.type, it.categoryId) }
            val keyword = renamer?.keyword ?: offer.keyword
            val id = findUserRule(keyword, offer.type)?.id
            return { copy(ruleOffer = ruleOffer?.withLookedUp(offer.keyword, keyword, id)) }
        }

        /**
         * Updates the user rule with this keyword and type (read again, so a stale look-up cannot clash), else creates
         * one. An error keeps the step; a new rule or a new category with past matches asks about them, unless the user
         * skipped the step meanwhile; else it leaves.
         */
        internal suspend fun save(offer: RuleOfferState): CashEntryState.() -> CashEntryState {
            val old = findUserRule(offer.keyword, offer.type)
            val saved = saveRule(old?.id, offer.keyword, offer.merchantName, offer.categoryId)
            val rule =
                when (saved) {
                    is Outcome.Err -> return { copy(ruleOffer = offer.copy(error = saved.error)) }
                    is Outcome.Ok -> saved.value
                }
            val count = if (old?.categoryId == offer.categoryId) 0 else countMatches(rule)
            return {
                if (count > 0 && isVisible) copy(ruleOffer = offer, bulkApplyCount = count) else copy(isVisible = false)
            }
        }

        /** Sets the saved rule's category on the past rows it matches, then leaves. */
        internal suspend fun applyToPast(offer: RuleOfferState): CashEntryState.() -> CashEntryState {
            findUserRule(offer.keyword, offer.type)?.let { applyRule(it) }
            return { copy(isVisible = false) }
        }
    }
