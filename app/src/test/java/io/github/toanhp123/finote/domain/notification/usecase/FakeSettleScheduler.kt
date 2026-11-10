package io.github.toanhp123.finote.domain.notification.usecase

import io.github.toanhp123.finote.domain.notification.port.SettleScheduler

/**
 * Copies from the real scheduler: the two entry points, `scheduleAfterCapture` and `settleNow`.
 * Does not copy: WorkManager, so each call only counts and settles nothing.
 */
class FakeSettleScheduler : SettleScheduler {
    var afterCaptureCount = 0
        private set
    var settleNowCount = 0
        private set

    override fun scheduleAfterCapture() {
        afterCaptureCount++
    }

    override fun settleNow() {
        settleNowCount++
    }
}
