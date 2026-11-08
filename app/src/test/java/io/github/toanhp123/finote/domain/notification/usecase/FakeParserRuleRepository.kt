package io.github.toanhp123.finote.domain.notification.usecase

import io.github.toanhp123.finote.domain.notification.model.ParserRule
import io.github.toanhp123.finote.domain.notification.repository.ParserRuleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** Records what was applied and reports [written] as the count of rules written. */
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

    override fun observeRules(): Flow<List<ParserRule>> = stored
}
