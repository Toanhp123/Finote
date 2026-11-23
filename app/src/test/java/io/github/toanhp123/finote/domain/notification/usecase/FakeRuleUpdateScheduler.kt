package io.github.toanhp123.finote.domain.notification.usecase

import io.github.toanhp123.finote.domain.notification.port.RuleUpdateScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Copies from the real scheduler: the periodic on/off state, and the checking flag a test drives by hand.
 * Does not copy: WorkManager, so `checkNow` only counts calls and runs nothing.
 */
class FakeRuleUpdateScheduler : RuleUpdateScheduler {
    var isPeriodicEnabled = false
        private set
    var checkNowCount = 0
        private set
    val isChecking = MutableStateFlow(false)

    override fun enablePeriodic() {
        isPeriodicEnabled = true
    }

    override fun disablePeriodic() {
        isPeriodicEnabled = false
    }

    override fun checkNow() {
        checkNowCount++
    }

    override fun observeIsChecking(): Flow<Boolean> = isChecking
}
