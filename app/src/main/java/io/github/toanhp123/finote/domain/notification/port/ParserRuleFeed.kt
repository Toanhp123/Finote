package io.github.toanhp123.finote.domain.notification.port

import io.github.toanhp123.finote.domain.common.Outcome
import io.github.toanhp123.finote.domain.notification.error.RuleFeedError
import io.github.toanhp123.finote.domain.notification.model.ParserRule

/** Source of the signed rule feed. */
interface ParserRuleFeed {
    /** The verified rules of the feed, or why the feed could not be fetched or trusted. */
    suspend fun fetch(): Outcome<List<ParserRule>, RuleFeedError>
}
