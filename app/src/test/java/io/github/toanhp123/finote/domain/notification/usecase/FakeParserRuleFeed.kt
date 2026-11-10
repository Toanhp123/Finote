package io.github.toanhp123.finote.domain.notification.usecase

import io.github.toanhp123.finote.domain.notification.model.ParserRule
import io.github.toanhp123.finote.domain.notification.port.ParserRuleFeed

/**
 * Copies from the real feed: `fetch` returns the rules or throws the failure.
 * Does not copy: network, JSON parsing, or rule validation.
 */
class FakeParserRuleFeed(
    var rules: List<ParserRule> = emptyList(),
    var error: Throwable? = null,
) : ParserRuleFeed {
    override suspend fun fetch(): List<ParserRule> = error?.let { throw it } ?: rules
}
