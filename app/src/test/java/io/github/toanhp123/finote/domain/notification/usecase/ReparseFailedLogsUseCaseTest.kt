package io.github.toanhp123.finote.domain.notification.usecase

import io.github.toanhp123.finote.domain.notification.SECRET_KEYWORDS
import io.github.toanhp123.finote.domain.notification.model.NotificationStatus
import io.github.toanhp123.finote.domain.notification.testLog
import io.github.toanhp123.finote.domain.notification.testRule
import io.github.toanhp123.finote.domain.transaction.model.TransactionType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReparseFailedLogsUseCaseTest {
    private fun rule(keywords: List<String> = emptyList()) =
        testRule(
            packageName = PKG,
            ignoreKeywords = keywords,
            regexPattern = "(?<sign>[+-])(?<amount>[\\d,]+)",
        )

    private fun failedLog(
        id: String,
        content: String,
    ) = testLog(
        id = id,
        packageName = PKG,
        channelId = "ch",
        title = "Biến động số dư",
        content = content,
        contentHash = "hash-$id",
        receivedAt = 2_000L,
        status = NotificationStatus.FAILED,
        failureReason = "no_rule_matched",
    )

    private val scheduler = FakeSettleScheduler()

    private fun useCase(repo: FakeNotificationCaptureRepository) =
        ReparseFailedLogsUseCase(repo, UnconfinedTestDispatcher(), scheduler)

    @Test
    fun `FAILED becomes PENDING with parsed fields`() =
        runTest {
            val repo = FakeNotificationCaptureRepository(listOf(rule()))
            repo.logs += failedLog("a", "SD TK -50,000 tai quan")

            assertEquals(1, useCase(repo)(setOf(PKG)))

            val log = repo.logs.single()
            assertEquals(NotificationStatus.PENDING, log.status)
            assertEquals("r1", log.matchedRuleId)
            assertEquals(50_000L, log.parsed?.amount)
            assertEquals(TransactionType.EXPENSE, log.parsed?.type)
            assertNull(log.failureReason)
        }

    @Test
    fun `FAILED blocked by a new keyword becomes IGNORED_SPAM and keeps text`() =
        runTest {
            val repo = FakeNotificationCaptureRepository(listOf(rule(keywords = listOf("khuyen mai"))))
            repo.logs += failedLog("a", "khuyen mai giam 50%")

            assertEquals(1, useCase(repo)(setOf(PKG)))

            val log = repo.logs.single()
            assertEquals(NotificationStatus.IGNORED_SPAM, log.status)
            assertEquals("keyword:khuyen mai", log.failureReason)
            assertEquals("khuyen mai giam 50%", log.content)
        }

    @Test
    fun `FAILED with a secret keyword becomes IGNORED_SPAM with blank text`() =
        runTest {
            val repo = FakeNotificationCaptureRepository(listOf(rule()))
            repo.logs += failedLog("a", "${SECRET_KEYWORDS.first()} 123456 -50,000")

            assertEquals(1, useCase(repo)(setOf(PKG)))

            val log = repo.logs.single()
            assertEquals(NotificationStatus.IGNORED_SPAM, log.status)
            assertEquals("secret:${SECRET_KEYWORDS.first()}", log.failureReason)
            assertEquals("", log.title)
            assertEquals("", log.content)
        }

    @Test
    fun `a package with no rules is skipped and nothing is written`() =
        runTest {
            val repo = FakeNotificationCaptureRepository(emptyList())
            val original = failedLog("a", "SD TK -50,000")
            repo.logs += original

            assertEquals(0, useCase(repo)(setOf(PKG)))

            assertEquals(original, repo.logs.single())
            assertEquals(0, repo.updateCount)
        }

    @Test
    fun `still no match stays FAILED and is not written`() =
        runTest {
            val repo = FakeNotificationCaptureRepository(listOf(rule()))
            val original = failedLog("a", "khong co so tien")
            repo.logs += original
            repo.failingUpdateIds += "a" // any write would throw and be skipped, so check the count too

            assertEquals(0, useCase(repo)(setOf(PKG)))

            assertEquals(original, repo.logs.single())
        }

    @Test
    fun `a row that throws is skipped`() =
        runTest {
            val repo = FakeNotificationCaptureRepository(listOf(rule()))
            repo.logs += failedLog("bad", "SD TK -10,000")
            repo.logs += failedLog("good", "SD TK -20,000")
            repo.failingUpdateIds += "bad"

            assertEquals(1, useCase(repo)(setOf(PKG)))

            assertEquals(NotificationStatus.FAILED, repo.logs.first { it.id == "bad" }.status)
            assertEquals(NotificationStatus.PENDING, repo.logs.first { it.id == "good" }.status)
        }

    @Test
    fun `reparse that makes a PENDING log schedules settle once`() =
        runTest {
            val repo = FakeNotificationCaptureRepository(listOf(rule()))
            repo.logs += failedLog("a", "SD TK -10,000")
            repo.logs += failedLog("b", "SD TK -20,000")

            assertEquals(2, useCase(repo)(setOf(PKG)))

            assertEquals(1, scheduler.afterCaptureCount)
        }

    @Test
    fun `reparse that only blocks logs does not schedule settle`() =
        runTest {
            val repo = FakeNotificationCaptureRepository(listOf(rule(keywords = listOf("khuyen mai"))))
            repo.logs += failedLog("a", "khuyen mai giam 50%")

            assertEquals(1, useCase(repo)(setOf(PKG)))

            assertEquals(0, scheduler.afterCaptureCount)
        }

    @Test
    fun `no change does not schedule settle`() =
        runTest {
            val repo = FakeNotificationCaptureRepository(listOf(rule()))
            repo.logs += failedLog("a", "khong co so tien")

            assertEquals(0, useCase(repo)(setOf(PKG)))

            assertEquals(0, scheduler.afterCaptureCount)
        }

    private companion object {
        const val PKG = "com.bank.app"
    }
}
