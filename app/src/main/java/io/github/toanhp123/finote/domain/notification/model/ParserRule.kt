package io.github.toanhp123.finote.domain.notification.model

/** Filtering part of a bank rule. P2b adds the regex fields. */
data class ParserRule(
    val id: String,
    val packageName: String,
    val bankName: String,
    /** Null means every channel is accepted. */
    val channelIdAllowlist: List<String>?,
    val ignoreKeywords: List<String>,
    val priority: Int,
    val isUserCustom: Boolean,
    val version: Int,
)
