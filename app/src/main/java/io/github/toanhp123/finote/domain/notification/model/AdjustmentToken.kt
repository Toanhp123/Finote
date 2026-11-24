package io.github.toanhp123.finote.domain.notification.model

/**
 * What the user confirmed when a balance adjustment may already include an inbox booking: the account, the time
 * [since] which an adjustment counts, and the timestamp of the newest adjustment after it.
 */
data class AdjustmentToken(
    val accountId: String,
    val since: Long,
    val newestAdjustmentAt: Long,
) {
    // Account and times stay out of logs and crash reports.
    override fun toString() = "AdjustmentToken"
}
