package io.github.toanhp123.finote.domain.notification.service

import io.github.toanhp123.finote.domain.notification.model.Classification
import io.github.toanhp123.finote.domain.notification.model.NotificationStatus
import io.github.toanhp123.finote.domain.notification.model.ParseResult
import io.github.toanhp123.finote.domain.notification.model.ParserRule
import io.github.toanhp123.finote.domain.notification.model.RawNotification

/** Reason prefix of a log hidden by a user ad keyword; the rest is the normalized keyword. */
const val USER_KEYWORD_REASON_PREFIX = "user_keyword:"

/**
 * Pure capture decision: secret check, then channel and keyword filter, then parse, and only when the parse
 * finds nothing, the user's ad keywords ([userKeywords], normalized). A secret or blocked notification is never
 * parsed, and a user keyword never hides a notification a rule parses.
 * Reasons: `secret:<kw>`, `channel:<id>`, `keyword:<kw>`, `user_keyword:<kw>`, `no_rule_matched`.
 */
fun classifyNotification(
    raw: RawNotification,
    rules: List<ParserRule>,
    userKeywords: List<String> = emptyList(),
): Classification {
    val text = "${raw.title}\n${raw.content}"
    val secret = firstMatchingKeyword(text, SECRET_KEYWORDS)
    val reason = if (secret != null) "secret:$secret" else ignoreReason(raw, rules, text)
    if (reason != null) return Classification(NotificationStatus.IGNORED_SPAM, reason, null, secret != null)
    val parsed = parseNotification(raw.title, raw.content, rules) as? ParseResult.Parsed
    return if (parsed != null) {
        Classification(NotificationStatus.PENDING, null, parsed, false)
    } else {
        unparsed(text, userKeywords)
    }
}

private fun unparsed(
    text: String,
    userKeywords: List<String>,
): Classification {
    val userKeyword = firstMatchingKeyword(text, userKeywords)
    return if (userKeyword != null) {
        val reason = USER_KEYWORD_REASON_PREFIX + normalizeKeyword(userKeyword)
        Classification(NotificationStatus.IGNORED_SPAM, reason, null, false)
    } else {
        Classification(NotificationStatus.FAILED, FAIL_NO_RULE_MATCHED, null, false)
    }
}

private fun ignoreReason(
    raw: RawNotification,
    rules: List<ParserRule>,
    text: String,
): String? {
    // One rule without an allowlist accepts every channel, so the filter needs all rules to have one.
    if (rules.all { it.channelIdAllowlist != null }) {
        val allowed = rules.flatMap { it.channelIdAllowlist.orEmpty() }
        if (raw.channelId !in allowed) return "channel:${raw.channelId}"
    }
    val keywords = rules.flatMapTo(LinkedHashSet()) { it.ignoreKeywords }.toList()
    return firstMatchingKeyword(text, keywords)?.let { "keyword:$it" }
}
