package io.github.toanhp123.finote.data.notification

import io.github.toanhp123.finote.domain.notification.NotificationCorpusFixtures
import io.github.toanhp123.finote.domain.notification.NotificationCorpusItem
import io.github.toanhp123.finote.domain.notification.SECRET_KEYWORDS
import io.github.toanhp123.finote.domain.notification.model.CaptureResult
import io.github.toanhp123.finote.domain.notification.model.RawNotification
import io.github.toanhp123.finote.domain.notification.normalizeForMatch
import io.github.toanhp123.finote.domain.notification.usecase.CaptureNotificationUseCase
import io.github.toanhp123.finote.domain.notification.usecase.FakeNotificationCaptureRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * The gate for the seed keywords: every ignored corpus sample is blocked, no transaction sample is.
 * Failure messages list item ids only, never notification text.
 */
class SystemParserRulesCorpusTest {
    private val clock = Clock.fixed(Instant.parse("2026-11-07T00:00:00Z"), ZoneOffset.UTC)

    // A fresh repository per call: the corpus reuses postTime values, and a shared one would report Duplicate.
    private suspend fun capture(
        packageName: String,
        title: String,
        content: String,
        channelId: String? = null,
        postTime: Long = 1L,
    ): CaptureResult {
        val repo = FakeNotificationCaptureRepository(SystemParserRules.map { it.toDomain() })
        return CaptureNotificationUseCase(
            repo,
            clock,
        )(RawNotification(packageName, channelId, title, content, postTime))
    }

    private suspend fun resultOf(item: NotificationCorpusItem) =
        capture(item.packageName, item.title, item.bigText ?: item.content, item.channelId, item.postTime)

    @Test
    fun `every ignored sample is blocked`() =
        runTest {
            val leaked = NotificationCorpusFixtures.ignoredOrSpam().filter { resultOf(it) !is CaptureResult.Ignored }

            assertEquals("not blocked: ${leaked.map { it.id }}", emptyList<String>(), leaked.map { it.id })
        }

    @Test
    fun `no transaction sample is blocked`() =
        runTest {
            val blocked =
                NotificationCorpusFixtures.financialTransactions().filter { resultOf(it) != CaptureResult.Stored }

            assertEquals("wrongly blocked: ${blocked.map { it.id }}", emptyList<String>(), blocked.map { it.id })
        }

    @Test
    fun `payments that merely contain a spam-like word stay pending`() =
        runTest {
            val texts =
                listOf(
                    "SD TK -1,200,000 VND. ND: Thanh toan Cong ty Thiet bi Y te ABC",
                    "Thanh toan 50.000d tai Canh Bao Store",
                    "Tui Than Tai sinh loi +123d",
                    "ND: chuc mung sinh nhat em",
                    "Thanh toan voucher Got It 200.000d",
                    "ND: phe duyet ho so",
                    "Doi qua Shop -30,000 VND",
                    "Hoan tien khuyen mai +20.000d",
                )
            val blocked =
                SystemParserRules.map { it.packageName }.flatMap { pkg ->
                    texts.indices.filter { capture(pkg, "Bien dong so du", texts[it]) != CaptureResult.Stored }
                }

            assertEquals("wrongly blocked text indexes: $blocked", emptyList<Int>(), blocked)
        }

    @Test
    fun `seed keywords hold no secret keyword`() {
        val keywords = SystemParserRules.flatMap { it.toDomain().ignoreKeywords }.map { normalizeForMatch(it) }

        assertTrue(keywords.none { it in SECRET_KEYWORDS.map(::normalizeForMatch) })
    }

    @Test
    fun `keyword fragments inside names do not block`() =
        runTest {
            assertEquals(CaptureResult.Stored, capture("com.VCB", "VCB Digibank", "Thanh toan QR tai Kichi Hotpot"))
            assertEquals(CaptureResult.Stored, capture("com.VCB", "VCB Digibank", "ND: Luu Dai Trang chuyen tien"))
        }
}
