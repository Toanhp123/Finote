package io.github.toanhp123.finote.work

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Configuration
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkRuleUpdateSchedulerTest {
    private lateinit var workManager: WorkManager
    private lateinit var scheduler: WorkRuleUpdateScheduler

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        // The real worker needs Hilt arguments, so a never-finishing stand-in keeps unconstrained work from failing.
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
        scheduler = WorkRuleUpdateScheduler(context)
    }

    private class HangingWorker(
        context: Context,
        params: WorkerParameters,
    ) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result = awaitCancellation()
    }

    private fun infos(name: String) = workManager.getWorkInfosForUniqueWork(name).get()

    @Test
    fun `enablePeriodic twice keeps one periodic work`() {
        scheduler.enablePeriodic()
        val first = infos("rule-update-periodic").single()
        scheduler.enablePeriodic()

        assertEquals(first.id, infos("rule-update-periodic").single().id)
        assertEquals(NetworkType.CONNECTED, first.constraints.requiredNetworkType)
    }

    @Test
    fun `disablePeriodic cancels it`() {
        scheduler.enablePeriodic()
        scheduler.disablePeriodic()

        assertEquals(WorkInfo.State.CANCELLED, infos("rule-update-periodic").single().state)
    }

    @Test
    fun `checkNow has no network constraint`() {
        scheduler.checkNow()

        assertEquals(
            NetworkType.NOT_REQUIRED,
            infos("rule-update-now")
                .single()
                .constraints.requiredNetworkType,
        )
    }

    @Test
    fun `observeIsChecking is true while now is enqueued`() =
        runTest {
            assertEquals(false, scheduler.observeIsChecking().first())

            scheduler.checkNow()

            assertTrue(scheduler.observeIsChecking().first())
        }
}
