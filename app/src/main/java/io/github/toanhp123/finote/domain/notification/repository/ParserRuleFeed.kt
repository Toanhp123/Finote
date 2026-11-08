package io.github.toanhp123.finote.domain.notification.repository

import io.github.toanhp123.finote.domain.notification.model.ParserRule
import io.github.toanhp123.finote.domain.notification.model.RuleFeedException

/** Source of the signed rule feed. */
interface ParserRuleFeed {
    /** The verified rules of the feed. Throws [RuleFeedException] when the feed cannot be fetched or trusted. */
    suspend fun fetch(): List<ParserRule>
}
