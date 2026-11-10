package io.github.toanhp123.finote.domain.notification.port

/** Schedules the settlement of `PENDING` logs. The implementation owns the background mechanism. */
interface SettleScheduler {
    /**
     * After a log became `PENDING`: run in 35 s. Keeps a settle that is already queued, running or in backoff, so a
     * burst of captures cannot push it back.
     * shortcut: a capture landing in the last moments of a run waits for the next capture or app open; fine for now,
     * revisit if logs are seen waiting on a real device.
     */
    fun scheduleAfterCapture()

    /** On app open: run now, replacing any queued or running settle. */
    fun settleNow()
}
