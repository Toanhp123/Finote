package io.github.toanhp123.finote.domain.notification.model

/** Ids of the inbox items the user has opened the inbox for ([seenIds]) and closed the banner for. Ids only. */
data class InboxMarkers(
    val seenIds: Set<String>,
    val bannerDismissedIds: Set<String>,
) {
    // The ids point at notification logs; keep them out of logs and crash reports.
    override fun toString() = "InboxMarkers(seen=${seenIds.size}, bannerDismissed=${bannerDismissedIds.size})"
}
