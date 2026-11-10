package io.github.toanhp123.finote.domain.notification.service

import io.github.toanhp123.finote.domain.notification.model.Classification
import io.github.toanhp123.finote.domain.notification.model.NotificationStatus
import io.github.toanhp123.finote.domain.notification.model.ParseResult
import io.github.toanhp123.finote.domain.notification.model.ParserRule
import io.github.toanhp123.finote.domain.notification.model.RawNotification

/**
 * Pure capture decision: secret check, then channel and keyword filter, then parse.
 * A secret or blocked notification is never parsed. Reasons: `secret:<kw>`, `channel:<id>`, `keyword:<kw>`,
 * `no_rule_matched`.
 */
fun classifyNotification(
    raw: RawNotification,
    rules: List<ParserRule>,
): Classification {
    val text = "${raw.title}\n${raw.content}"
    val secret = firstMatchingKeyword(text, SECRET_KEYWORDS)
    val reason = if (secret != null) "secret:$secret" else ignoreReason(raw, rules, text)
    if (reason != null) return Classification(NotificationStatus.IGNORED_SPAM, reason, null, secret != null)
    val parsed = parseNotification(raw.title, raw.content, rules) as? ParseResult.Parsed
    return if (parsed != null) {
        Classification(NotificationStatus.PENDING, null, parsed, false)
    } else {
        Classification(NotificationStatus.FAILED, "no_rule_matched", null, false)
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
