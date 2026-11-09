package io.github.toanhp123.finote.domain.notification.usecase

import io.github.toanhp123.finote.domain.notification.repository.SettleScheduler

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
