package io.github.toanhp123.finote.domain.category.usecase

import io.github.toanhp123.finote.domain.category.model.KeywordRule
import io.github.toanhp123.finote.domain.category.model.RuleMatch
import io.github.toanhp123.finote.domain.category.repository.KeywordRuleRepository
import io.github.toanhp123.finote.domain.common.normalizeKeyword
import io.github.toanhp123.finote.domain.transaction.model.Transaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Copies from the real repository: a user write forces the user shape and never touches a system rule, a delete
 * removes only a user rule. Does not copy the row selection of the bulk apply: [uncategorized] is served as given,
 * and `setCategory` only records its [categorized] rows.
 */
class FakeKeywordRuleRepository(
    rules: List<KeywordRule> = emptyList(),
    var uncategorized: List<Transaction> = emptyList(),
) : KeywordRuleRepository {
    private val state = MutableStateFlow(rules)
    val rules: List<KeywordRule> get() = state.value
    val categorized = mutableListOf<Pair<String, RuleMatch>>()

    override fun observeRules(): Flow<List<KeywordRule>> = state

    override suspend fun getRules(): List<KeywordRule> = rules

    override suspend fun getRule(id: String): KeywordRule? = rules.firstOrNull { it.id == id }

    override suspend fun upsertUserRule(rule: KeywordRule) {
        if (getRule(rule.id)?.isUserDefined == false) return
        val stored =
            rule.copy(
                normalizedKeyword = normalizeKeyword(rule.keyword),
                priority = 0,
                isUserDefined = true,
                version = 0,
            )
        state.value = rules.filterNot { it.id == rule.id } + stored
    }

    override suspend fun deleteUserRule(id: String) {
        state.value = rules.filterNot { it.id == id && it.isUserDefined }
    }

    override suspend fun setActive(
        id: String,
        active: Boolean,
        now: Long,
    ) {
        state.value = rules.map { if (it.id == id) it.copy(isActive = active, updatedAt = now) else it }
    }

    override suspend fun getUncategorizedNotificationRows(): List<Transaction> = uncategorized

    override suspend fun setCategory(rows: List<Pair<String, RuleMatch>>) {
        categorized += rows
    }
}
