package io.github.toanhp123.finote.domain.notification.port

import io.github.toanhp123.finote.domain.notification.error.RuleFeedException
import io.github.toanhp123.finote.domain.notification.model.ParserRule

/** Source of the signed rule feed. */
interface ParserRuleFeed {
    /** The verified rules of the feed. Throws [RuleFeedException] when the feed cannot be fetched or trusted. */
    suspend fun fetch(): List<ParserRule>
}
