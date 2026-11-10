package io.github.toanhp123.finote.domain.notification.usecase

import io.github.toanhp123.finote.domain.notification.model.NotificationStatus
import io.github.toanhp123.finote.domain.notification.testLog
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class FakeNotificationSettlementRepositoryTest {
    @Test
    fun `settled logs are filtered and sorted by postTime then id`() =
        runTest {
            val repository =
                FakeNotificationSettlementRepository(
                    logs =
                        mutableListOf(
                            testLog(id = "b", postTime = 20, status = NotificationStatus.CONFIRMED),
                            testLog(id = "a", postTime = 20, status = NotificationStatus.FAILED),
                            testLog(id = "first", postTime = 10, status = NotificationStatus.DEDUPLICATED),
                            testLog(id = "too-old", postTime = 5, status = NotificationStatus.CONFIRMED),
                            testLog(id = "pending", postTime = 30, status = NotificationStatus.PENDING),
                            testLog(id = "spam", postTime = 30, status = NotificationStatus.IGNORED_SPAM),
                        ),
                )

            assertEquals(listOf("first", "a", "b"), repository.getSettledLogsSince(10).map { it.id })
        }
}
