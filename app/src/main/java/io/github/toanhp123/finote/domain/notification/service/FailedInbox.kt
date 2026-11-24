package io.github.toanhp123.finote.domain.notification.service

import io.github.toanhp123.finote.domain.notification.model.FailedInboxItem
import io.github.toanhp123.finote.domain.notification.model.NotificationLog
import io.github.toanhp123.finote.domain.notification.model.NotificationStatus

private val OLDEST_FIRST = compareBy<NotificationLog> { it.postTime }.thenBy { it.id }
private val SETTLEMENT_REASONS = INBOX_REASONS - FAIL_NO_RULE_MATCHED

/**
 * The inbox items among [logs]: failed logs with a shown reason, and logs a user keyword hid. A log of a package
 * outside [activePackages] gives no item. Identical `no_rule_matched` copies are one item whose representative is the
 * oldest copy, so its id stays the same when another identical copy arrives. Items are newest first. A log booked
 * through a later version of the same notification is dropped by the repository before this runs.
 */
fun buildFailedInbox(
    logs: List<NotificationLog>,
    activePackages: Set<String>,
): List<FailedInboxItem> {
    val active = logs.filter { it.packageName in activePackages }
    val settlement = active.filter { it.failedReason() in SETTLEMENT_REASONS }
    val unparsed = active.filter { it.failedReason() == FAIL_NO_RULE_MATCHED }
    val keywordHidden = active.filter { it.status == NotificationStatus.IGNORED_SPAM && it.isKeywordReason() }
    return (
        settlement.map { it.toItem(listOf(it.id), isHidden = false, isKeywordHidden = false) } +
            unparsed
                .groupBy { Triple(it.packageName, it.title, it.content) }
                .values
                .map { copies -> copies.sortedWith(OLDEST_FIRST).toUnparsedItem() } +
            keywordHidden.map { it.toItem(listOf(it.id), isHidden = true, isKeywordHidden = true) }
    ).sortedWith(compareByDescending<FailedInboxItem> { it.postTime }.thenByDescending { it.id })
}

private fun NotificationLog.failedReason(): String? = failureReason.takeIf { status == NotificationStatus.FAILED }

private fun NotificationLog.isKeywordReason() = failureReason?.startsWith(USER_KEYWORD_REASON_PREFIX) == true

private fun List<NotificationLog>.toUnparsedItem(): FailedInboxItem {
    val oldest = first()
    return oldest.toItem(
        logIds = map { it.id },
        isHidden = !looksLikeMoney(oldest.title, oldest.content),
        isKeywordHidden = false,
    )
}

private fun NotificationLog.toItem(
    logIds: List<String>,
    isHidden: Boolean,
    isKeywordHidden: Boolean,
) = FailedInboxItem(
    id = id,
    logIds = logIds,
    packageName = packageName,
    title = title,
    content = content,
    postTime = postTime,
    receivedAt = receivedAt,
    reason = requireNotNull(failureReason),
    parsed = parsed,
    isHidden = isHidden,
    isKeywordHidden = isKeywordHidden,
)
