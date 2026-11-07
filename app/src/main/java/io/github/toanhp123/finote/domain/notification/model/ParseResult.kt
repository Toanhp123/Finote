package io.github.toanhp123.finote.domain.notification.model

/** Outcome of running the parser rules over one notification. */
sealed interface ParseResult {
    data class Parsed(
        val ruleId: String,
        val fields: ParsedFields,
    ) : ParseResult

    data object NoMatch : ParseResult
}
