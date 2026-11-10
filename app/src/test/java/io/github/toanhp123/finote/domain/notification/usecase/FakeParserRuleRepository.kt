package io.github.toanhp123.finote.domain.notification.usecase

import io.github.toanhp123.finote.domain.notification.model.ParserRule
import io.github.toanhp123.finote.domain.notification.repository.ParserRuleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * Copies from the real repository: `observeRules` order (packageName, priority, id).
 * Does not copy: version and `isUserCustom` checks in `applyRules`; it records the input and reports [written].
 */
class FakeParserRuleRepository(
    var written: Int = 0,
    var error: Throwable? = null,
) : ParserRuleRepository {
    val applied = mutableListOf<ParserRule>()
    val stored = MutableStateFlow<List<ParserRule>>(emptyList())

    override suspend fun applyRules(rules: List<ParserRule>): Int {
        error?.let { throw it }
        applied += rules
        return written
    }

    override fun observeRules(): Flow<List<ParserRule>> =
        stored.map { rules -> rules.sortedWith(compareBy({ it.packageName }, { it.priority }, { it.id })) }
}
