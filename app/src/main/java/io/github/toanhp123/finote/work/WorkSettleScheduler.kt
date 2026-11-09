package io.github.toanhp123.finote.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.toanhp123.finote.domain.notification.repository.SettleScheduler
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class WorkSettleScheduler
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) : SettleScheduler {
        private val workManager by lazy { WorkManager.getInstance(context) }

        override fun scheduleAfterCapture() = enqueue(AFTER_CAPTURE_DELAY_SECONDS)

        override fun settleNow() = enqueue(0L)

        private fun enqueue(delaySeconds: Long) {
            val request =
                OneTimeWorkRequestBuilder<SettleWorker>()
                    .setInitialDelay(delaySeconds, TimeUnit.SECONDS)
                    .setBackoffCriteria(BackoffPolicy.LINEAR, BACKOFF_SECONDS, TimeUnit.SECONDS)
                    .build()
            workManager.enqueueUniqueWork(NAME, ExistingWorkPolicy.REPLACE, request)
        }

        private companion object {
            const val NAME = "settle-notifications"
            const val AFTER_CAPTURE_DELAY_SECONDS = 35L
            const val BACKOFF_SECONDS = 30L
        }
    }
