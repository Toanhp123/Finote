package io.github.toanhp123.finote.work

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Configuration
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import kotlinx.coroutines.awaitCancellation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class WorkSettleSchedulerTest {
    private lateinit var workManager: WorkManager
    private lateinit var scheduler: WorkSettleScheduler

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        // The real worker needs Hilt arguments, so a never-finishing stand-in keeps the work from running.
        val factory =
            object : WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: WorkerParameters,
                ): ListenableWorker = HangingWorker(appContext, workerParameters)
            }
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration
                .Builder()
                .setExecutor(SynchronousExecutor())
                .setWorkerFactory(factory)
                .build(),
        )
        workManager = WorkManager.getInstance(context)
        scheduler = WorkSettleScheduler(context)
    }

    private class HangingWorker(
        context: Context,
        params: WorkerParameters,
    ) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result = awaitCancellation()
    }

    private fun infos() = workManager.getWorkInfosForUniqueWork("settle-notifications").get()

    @Test
    fun `scheduleAfterCapture enqueues with 35 s delay`() {
        scheduler.scheduleAfterCapture()

        val info = infos().single()
        assertEquals(WorkInfo.State.ENQUEUED, info.state)
        assertEquals(TimeUnit.SECONDS.toMillis(35), info.initialDelayMillis)
    }

    @Test
    fun `settleNow enqueues with no delay`() {
        scheduler.settleNow()

        assertEquals(0L, infos().single().initialDelayMillis)
    }

    @Test
    fun `second call replaces the first`() {
        scheduler.scheduleAfterCapture()
        val first = infos().single()
        scheduler.settleNow()

        // REPLACE drops the old request, so exactly one request is left and it is the new one.
        val current = infos().single()
        assertNotEquals(first.id, current.id)
        assertEquals(0L, current.initialDelayMillis)
    }
}
