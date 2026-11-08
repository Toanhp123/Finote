package io.github.toanhp123.finote.domain.notification.repository

import kotlinx.coroutines.flow.Flow

/** Schedules the parser rule update. The implementation owns the background mechanism. */
interface RuleUpdateScheduler {
    /** Runs the update every 24 hours on a network. A second call keeps the running schedule. */
    fun enablePeriodic()

    fun disablePeriodic()

    /** Runs one update now, with no network constraint. */
    fun checkNow()

    /** True while a manual check waits or runs, or while the periodic one runs. */
    fun observeIsChecking(): Flow<Boolean>
}
