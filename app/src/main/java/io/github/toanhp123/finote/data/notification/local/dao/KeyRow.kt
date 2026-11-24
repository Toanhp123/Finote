package io.github.toanhp123.finote.data.notification.local.dao

/** What the inbox needs from a handled log: its package, platform key, time and parsed amount. */
data class KeyRow(
    val packageName: String,
    val notificationKey: String,
    val postTime: Long,
    val parsedAmount: Long?,
) {
    // The key and the amount stay out of logs and crash reports.
    override fun toString() = "KeyRow(packageName=$packageName)"
}
