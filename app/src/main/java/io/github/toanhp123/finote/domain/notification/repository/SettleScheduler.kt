package io.github.toanhp123.finote.domain.notification.repository

/** Schedules the settlement of `PENDING` logs. The implementation owns the background mechanism. */
interface SettleScheduler {
    /** After a log became `PENDING`: run in 35 s, replacing any queued or running settle. */
    fun scheduleAfterCapture()

    /** On app open: run now, replacing any queued or running settle. */
    fun settleNow()
}
