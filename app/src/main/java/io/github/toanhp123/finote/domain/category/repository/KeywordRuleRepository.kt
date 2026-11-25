package io.github.toanhp123.finote.domain.category.repository

import io.github.toanhp123.finote.domain.category.model.KeywordRule
import io.github.toanhp123.finote.domain.category.model.RuleMatch
import io.github.toanhp123.finote.domain.transaction.model.Transaction
import kotlinx.coroutines.flow.Flow

/** System rules are seeded and can only be switched on or off; user rules can also be added, changed and deleted. */
interface KeywordRuleRepository {
    fun observeRules(): Flow<List<KeywordRule>>

    suspend fun getRules(): List<KeywordRule>

    suspend fun getRule(id: String): KeywordRule?

    /** Forces the user shape (`isUserDefined`, priority 0, version 0) and the normalized keyword. A system id stays. */
    suspend fun upsertUserRule(rule: KeywordRule)

    /** Deletes a user rule for good. A system rule stays. */
    suspend fun deleteUserRule(id: String)

    /** Changes only `isActive` and `updatedAt`. */
    suspend fun setActive(
        id: String,
        active: Boolean,
        now: Long,
    )

    /** Live income and expense rows booked from a notification that have no category yet. */
    suspend fun getUncategorizedNotificationRows(): List<Transaction>

    /**
     * Gives each row (by id) the category and merchant name of its match. A null name keeps the stored one.
     * Leaves `updatedAt` alone: this is the app's own guess, not an edit by the user.
     */
    suspend fun setCategory(rows: List<Pair<String, RuleMatch>>)
}
