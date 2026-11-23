package io.github.toanhp123.finote.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.toanhp123.finote.domain.notification.port.RuleUpdateScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class WorkRuleUpdateScheduler
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) : RuleUpdateScheduler {
        private val workManager by lazy { WorkManager.getInstance(context) }

        override fun enablePeriodic() {
            val request =
                PeriodicWorkRequestBuilder<RuleUpdateWorker>(PERIOD_HOURS, TimeUnit.HOURS)
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .setInputData(workDataOf(RuleUpdateWorker.KEY_IS_MANUAL to false))
                    .build()
            workManager.enqueueUniquePeriodicWork(PERIODIC_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        override fun disablePeriodic() {
            workManager.cancelUniqueWork(PERIODIC_NAME)
        }

        override fun checkNow() {
            val request =
                OneTimeWorkRequestBuilder<RuleUpdateWorker>()
                    .setInputData(workDataOf(RuleUpdateWorker.KEY_IS_MANUAL to true))
                    .build()
            workManager.enqueueUniqueWork(NOW_NAME, ExistingWorkPolicy.KEEP, request)
        }

        override fun observeIsChecking(): Flow<Boolean> =
            combine(
                workManager.getWorkInfosForUniqueWorkFlow(NOW_NAME),
                workManager.getWorkInfosForUniqueWorkFlow(PERIODIC_NAME),
            ) { now, periodic ->
                now.any { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.RUNNING } ||
                    periodic.any { it.state == WorkInfo.State.RUNNING }
            }.distinctUntilChanged()

        private companion object {
            const val PERIODIC_NAME = "rule-update-periodic"
            const val NOW_NAME = "rule-update-now"
            const val PERIOD_HOURS = 24L
        }
    }
