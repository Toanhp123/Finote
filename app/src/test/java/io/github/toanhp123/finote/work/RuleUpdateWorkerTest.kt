package io.github.toanhp123.finote.work

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import io.github.toanhp123.finote.domain.notification.model.RuleFeedError
import io.github.toanhp123.finote.domain.notification.model.RuleFeedException
import io.github.toanhp123.finote.domain.notification.model.RuleUpdateResult
import io.github.toanhp123.finote.domain.notification.usecase.FakeNotificationCaptureRepository
import io.github.toanhp123.finote.domain.notification.usecase.FakeParserRuleFeed
import io.github.toanhp123.finote.domain.notification.usecase.FakeParserRuleRepository
import io.github.toanhp123.finote.domain.notification.usecase.FakeRuleUpdateSettingsRepository
import io.github.toanhp123.finote.domain.notification.usecase.FakeSettleScheduler
import io.github.toanhp123.finote.domain.notification.usecase.ReparseFailedLogsUseCase
import io.github.toanhp123.finote.domain.notification.usecase.UpdateParserRulesUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class RuleUpdateWorkerTest {
    private val feed = FakeParserRuleFeed()
    private val rules = FakeParserRuleRepository()
    private val settings = FakeRuleUpdateSettingsRepository()

    private suspend fun run(isManual: Boolean): ListenableWorker.Result {
        val useCase =
            UpdateParserRulesUseCase(
                feed,
                rules,
                ReparseFailedLogsUseCase(
                    FakeNotificationCaptureRepository(emptyList()),
                    UnconfinedTestDispatcher(),
                    FakeSettleScheduler(),
                ),
                settings,
                Clock.systemUTC(),
            )
        val factory =
            object : WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: WorkerParameters,
                ): ListenableWorker = RuleUpdateWorker(appContext, workerParameters, useCase)
            }
        return TestListenableWorkerBuilder<RuleUpdateWorker>(ApplicationProvider.getApplicationContext())
            .setWorkerFactory(factory)
            .setInputData(workDataOf(RuleUpdateWorker.KEY_IS_MANUAL to isManual))
            .build()
            .doWork()
    }

    @Test
    fun `manual run without network fails and records NETWORK`() =
        runTest {
            feed.error = RuleFeedException(RuleFeedError.NETWORK, "timeout")

            assertEquals(ListenableWorker.Result.failure(), run(isManual = true))
            assertEquals(RuleUpdateResult.Network, settings.lastCheck.value?.result)
        }

    @Test
    fun `periodic run without network retries`() =
        runTest {
            feed.error = RuleFeedException(RuleFeedError.NETWORK, "timeout")

            assertEquals(ListenableWorker.Result.retry(), run(isManual = false))
        }

    @Test
    fun `invalid feed fails in both modes`() =
        runTest {
            feed.error = RuleFeedException(RuleFeedError.INVALID, "bad_signature")

            assertEquals(ListenableWorker.Result.failure(), run(isManual = false))
            assertEquals(ListenableWorker.Result.failure(), run(isManual = true))
        }

    @Test
    fun `database error retries when periodic and fails when manual`() =
        runTest {
            rules.error = IllegalStateException("db")

            assertEquals(ListenableWorker.Result.retry(), run(isManual = false))
            assertEquals(ListenableWorker.Result.failure(), run(isManual = true))
            assertEquals(RuleUpdateResult.Error, settings.lastCheck.value?.result)
        }

    @Test
    fun `update succeeds`() =
        runTest {
            rules.written = 2

            assertEquals(ListenableWorker.Result.success(), run(isManual = false))
            assertEquals(RuleUpdateResult.Updated(2), settings.lastCheck.value?.result)
        }
}
