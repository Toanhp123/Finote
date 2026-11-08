package io.github.toanhp123.finote.domain.notification.repository

import io.github.toanhp123.finote.domain.notification.model.ParserRule
import kotlinx.coroutines.flow.Flow

interface ParserRuleRepository {
    /**
     * Writes a rule when its id is missing, or when the stored copy is a system rule with a lower version.
     * A user rule is never overwritten and no version is lowered. Returns the number of rules written.
     */
    suspend fun applyRules(rules: List<ParserRule>): Int

    /** All stored rules. A row that cannot be read is dropped. */
    fun observeRules(): Flow<List<ParserRule>>
}
