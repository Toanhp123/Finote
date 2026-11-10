package io.github.toanhp123.finote.work

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import io.github.toanhp123.finote.domain.notification.model.NotificationLog
import io.github.toanhp123.finote.domain.notification.model.NotificationStatus
import io.github.toanhp123.finote.domain.notification.repository.NotificationSettlementRepository
import io.github.toanhp123.finote.domain.notification.usecase.FakeNotificationSettlementRepository
import io.github.toanhp123.finote.domain.notification.usecase.SettlePendingLogsUseCase
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@RunWith(AndroidJUnit4::class)
class SettleWorkerTest {
    private val clock = Clock.fixed(Instant.parse("2026-11-03T00:00:00Z"), ZoneOffset.UTC)

    private suspend fun run(repo: NotificationSettlementRepository): ListenableWorker.Result {
        val useCase = SettlePendingLogsUseCase(repo, clock)
        val factory =
            object : WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: WorkerParameters,
                ): ListenableWorker = SettleWorker(appContext, workerParameters, useCase)
            }
        return TestListenableWorkerBuilder<SettleWorker>(ApplicationProvider.getApplicationContext())
            .setWorkerFactory(factory)
            .build()
            .doWork()
    }

    private fun pendingLog() =
        NotificationLog(
            id = "a",
            packageName = "com.bank.app",
            channelId = null,
            title = "",
            content = "",
            contentHash = "hash-a",
            postTime = clock.millis(),
            receivedAt = 2_000L,
            status = NotificationStatus.PENDING,
            failureReason = null,
        )

    @Test
    fun `retries while logs wait`() =
        runTest {
            // Posted just now, so it is not due yet and stays PENDING.
            val repo = FakeNotificationSettlementRepository(mutableListOf(pendingLog()))

            assertEquals(ListenableWorker.Result.retry(), run(repo))
        }

    @Test
    fun `retries when settle throws`() =
        runTest {
            val failing =
                object : NotificationSettlementRepository by FakeNotificationSettlementRepository() {
                    override suspend fun getPendingLogs(): List<NotificationLog> = error("boom")
                }

            assertEquals(ListenableWorker.Result.retry(), run(failing))
        }

    @Test
    fun `succeeds when none wait`() =
        runTest {
            assertEquals(ListenableWorker.Result.success(), run(FakeNotificationSettlementRepository()))
        }
}
