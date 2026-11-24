package io.github.toanhp123.finote.domain.notification.model

/**
 * One row of the failed inbox. A `no_rule_matched` item stands for every identical copy: [id] is the oldest copy, so
 * it stays the same when another identical copy arrives, and [logIds] lists them all, oldest first. [isKeywordHidden]
 * marks a log a user keyword hid; it is also [isHidden].
 */
data class FailedInboxItem(
    val id: String,
    val logIds: List<String>,
    val packageName: String,
    val title: String,
    val content: String,
    val postTime: Long,
    val receivedAt: Long,
    val reason: String,
    val parsed: ParsedFields?,
    val isHidden: Boolean,
    val isKeywordHidden: Boolean,
) {
    // Text, package, ids of copies and parsed values stay out of logs and crash reports.
    override fun toString() = "FailedInboxItem(id=$id, reason=$reason, isHidden=$isHidden)"
}
