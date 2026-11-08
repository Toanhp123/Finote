package io.github.toanhp123.finote.domain.notification.model

enum class RuleFeedError { NETWORK, INVALID }

/** A rule feed that could not be fetched or trusted. [message] is a short reason code, never feed content. */
class RuleFeedException(
    val reason: RuleFeedError,
    message: String,
) : Exception(message)
