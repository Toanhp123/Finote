package io.github.toanhp123.finote.domain.notification.usecase

import io.github.toanhp123.finote.domain.common.Outcome
import io.github.toanhp123.finote.domain.notification.error.RuleFeedError
import io.github.toanhp123.finote.domain.notification.model.ParserRule
import io.github.toanhp123.finote.domain.notification.port.ParserRuleFeed

/**
 * Copies from the real feed: `fetch` returns the rules or the failure.
 * Does not copy: network, JSON parsing, or rule validation.
 */
class FakeParserRuleFeed(
    var rules: List<ParserRule> = emptyList(),
    var error: RuleFeedError? = null,
) : ParserRuleFeed {
    override suspend fun fetch(): Outcome<List<ParserRule>, RuleFeedError> =
        error?.let { Outcome.Err(it) } ?: Outcome.Ok(rules)
}
