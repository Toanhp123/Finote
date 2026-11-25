package io.github.toanhp123.finote.domain.category.usecase

import io.github.toanhp123.finote.domain.category.error.KeywordRuleError
import io.github.toanhp123.finote.domain.category.model.Category
import io.github.toanhp123.finote.domain.category.model.KeywordMatchType
import io.github.toanhp123.finote.domain.category.model.KeywordRule
import io.github.toanhp123.finote.domain.category.repository.CategoryRepository
import io.github.toanhp123.finote.domain.category.repository.KeywordRuleRepository
import io.github.toanhp123.finote.domain.common.Outcome
import io.github.toanhp123.finote.domain.common.TransactionRunner
import io.github.toanhp123.finote.domain.common.normalizeKeyword
import java.time.Clock
import java.util.UUID
import javax.inject.Inject

private const val MIN_KEYWORD_LENGTH = 2

class SaveKeywordRuleUseCase
    @Inject
    constructor(
        private val rules: KeywordRuleRepository,
        private val categories: CategoryRepository,
        private val tx: TransactionRunner,
        private val clock: Clock,
    ) {
        /**
         * Creates a user rule when [id] is null, else changes that user rule and keeps its `createdAt`, match type and
         * switch. A blank [merchantName] is stored as null. An id that is not a user rule is `NotFound`.
         */
        suspend operator fun invoke(
            id: String?,
            keyword: String,
            merchantName: String?,
            categoryId: String,
        ): Outcome<KeywordRule, KeywordRuleError> {
            val normalized = normalizeKeyword(keyword)
            if (normalized.length < MIN_KEYWORD_LENGTH) return Outcome.Err(KeywordRuleError.TooShort)
            val now = clock.millis()
            val newId = UUID.randomUUID().toString()
            return tx.write {
                val category = categories.getCategory(categoryId)
                val old = id?.let { rules.getRule(it) }
                if (category == null || category.deletedAt != null || category.isHidden) {
                    Outcome.Err(KeywordRuleError.CategoryGone)
                } else if (id != null && old?.isUserDefined != true) {
                    Outcome.Err(KeywordRuleError.NotFound)
                } else if (hasDuplicate(id, normalized, category)) {
                    Outcome.Err(KeywordRuleError.Duplicate)
                } else {
                    val rule =
                        KeywordRule(
                            id = id ?: newId,
                            keyword = keyword.trim(),
                            normalizedKeyword = normalized,
                            matchType = old?.matchType ?: KeywordMatchType.CONTAINS,
                            merchantName = merchantName?.trim()?.takeIf { it.isNotEmpty() },
                            categoryId = categoryId,
                            priority = 0,
                            isUserDefined = true,
                            isActive = old?.isActive ?: true,
                            version = 0,
                            createdAt = old?.createdAt ?: now,
                            updatedAt = now,
                        )
                    rules.upsertUserRule(rule)
                    Outcome.Ok(rule)
                }
            }
        }

        private suspend fun hasDuplicate(
            id: String?,
            normalized: String,
            category: Category,
        ): Boolean =
            rules.getRules().any {
                it.isUserDefined &&
                    it.id != id &&
                    it.normalizedKeyword == normalized &&
                    categories.getCategory(it.categoryId)?.type == category.type
            }
    }
