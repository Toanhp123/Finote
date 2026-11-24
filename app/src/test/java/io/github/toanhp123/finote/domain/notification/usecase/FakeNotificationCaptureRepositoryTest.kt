package io.github.toanhp123.finote.domain.notification.usecase

import io.github.toanhp123.finote.domain.notification.model.NotificationStatus
import io.github.toanhp123.finote.domain.notification.testLog
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class FakeNotificationCaptureRepositoryTest {
    private val repository = FakeNotificationCaptureRepository()

    private fun failed(
        id: String,
        receivedAt: Long,
        reason: String? = "no_rule_matched",
        packageName: String = "pkg",
    ) = testLog(
        id = id,
        receivedAt = receivedAt,
        packageName = packageName,
        status = NotificationStatus.FAILED,
        failureReason = reason,
    )

    @Test
    fun `reparse logs keep no_rule_matched and user_keyword logs of the packages, oldest first`() =
        runTest {
            repository.insertLog(failed("b", receivedAt = 2))
            repository.insertLog(failed("a", receivedAt = 2))
            repository.insertLog(failed("old", receivedAt = 1))
            repository.insertLog(failed("other-reason", receivedAt = 0, reason = "parse_error"))
            repository.insertLog(failed("other-pkg", receivedAt = 0, packageName = "elsewhere"))

            repository.insertLog(
                failed("hidden", receivedAt = 1, reason = "user_keyword:moi ban")
                    .copy(status = NotificationStatus.IGNORED_SPAM),
            )
            repository.insertLog(
                failed(
                    "rule-hidden",
                    receivedAt = 0,
                    reason = "keyword:x",
                ).copy(status = NotificationStatus.IGNORED_SPAM),
            )

            assertEquals(listOf("hidden", "old", "a", "b"), repository.getReparseLogs(setOf("pkg")).map { it.id })
        }
}
