package io.github.toanhp123.finote.domain.notification.usecase

import io.github.toanhp123.finote.domain.notification.contentHash
import io.github.toanhp123.finote.domain.notification.model.CaptureResult
import io.github.toanhp123.finote.domain.notification.model.NotificationStatus
import io.github.toanhp123.finote.domain.notification.model.ParserRule
import io.github.toanhp123.finote.domain.notification.model.RawNotification
import io.github.toanhp123.finote.domain.transaction.model.TransactionType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class CaptureNotificationUseCaseTest {
    private val now = Instant.parse("2026-11-07T01:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    // why: a test builder, every parameter is one knob of a rule
    @Suppress("LongParameterList")
    private fun rule(
        id: String = "r1",
        priority: Int = 0,
        keywords: List<String> = emptyList(),
        allowlist: List<String>? = null,
        pattern: String = "(?<sign>[+-])(?<amount>[\\d,]+)",
        fallback: TransactionType? = null,
        isActive: Boolean = true,
    ) = ParserRule(
        id = id,
        packageName = PKG,
        bankName = "Bank",
        channelIdAllowlist = allowlist,
        ignoreKeywords = keywords,
        priority = priority,
        isUserCustom = false,
        version = 1,
        regexPattern = pattern,
        fallbackTransactionType = fallback,
        dateFormatPattern = null,
        isActive = isActive,
        updatedAt = 0L,
    )

    private fun raw(
        title: String = "Biến động số dư",
        content: String = "SD TK -50,000 tai quan",
        channelId: String? = "ch",
        postTime: Long = 1_000L,
    ) = RawNotification(PKG, channelId, title, content, postTime)

    private class Env(
        val repo: FakeNotificationCaptureRepository,
        val useCase: CaptureNotificationUseCase,
    )

    private fun env(vararg rules: ParserRule): Env {
        val repo = FakeNotificationCaptureRepository(rules.toList())
        return Env(repo, CaptureNotificationUseCase(repo, clock))
    }

    @Test
    fun `package without rules is not tracked`() =
        runTest {
            val e = env()

            assertEquals(CaptureResult.NotTracked, e.useCase(raw()))
            assertTrue(e.repo.logs.isEmpty())
        }

    @Test
    fun `secret keyword stores metadata only for any tracked package`() =
        runTest {
            val e = env(rule())
            val input = raw(title = "Ngân hàng", content = "Mã OTP: 123456")

            assertEquals(CaptureResult.Ignored("secret:OTP"), e.useCase(input))

            val log = e.repo.logs.single()
            assertEquals(NotificationStatus.IGNORED_SPAM, log.status)
            assertEquals("", log.title)
            assertEquals("", log.content)
            assertEquals("secret:OTP", log.failureReason)
            assertEquals(contentHash(PKG, 1_000L, "Ngân hàng", "Mã OTP: 123456"), log.contentHash)
        }

    @Test
    fun `secret keywords are caught glued, accented and in the title`() =
        runTest {
            val e = env(rule())

            assertEquals(
                CaptureResult.Ignored("secret:SmartOTP"),
                e.useCase(raw(content = "Ma SmartOTP: 123456", postTime = 1L)),
            )
            assertEquals(CaptureResult.Ignored("secret:OTP"), e.useCase(raw(content = "Ma OTP123456", postTime = 2L)))
            assertEquals(
                CaptureResult.Ignored("secret:mã xác thực"),
                e.useCase(raw(title = "MÃ XÁC THỰC", content = "582910", postTime = 3L)),
            )
            assertTrue(e.repo.logs.all { it.title.isEmpty() && it.content.isEmpty() })
        }

    @Test
    fun `secret wins over a channel block`() =
        runTest {
            val e = env(rule(allowlist = listOf("TXN")))

            assertEquals(
                CaptureResult.Ignored("secret:OTP"),
                e.useCase(raw(content = "Mã OTP: 123456", channelId = "PROMOTION")),
            )
            assertEquals(
                "",
                e.repo.logs
                    .single()
                    .content,
            )
        }

    @Test
    fun `rule keyword stores the row but keeps title and content`() =
        runTest {
            val e = env(rule(keywords = listOf("ưu đãi đặc quyền")))
            val input = raw(title = "VCB", content = "Ưu đãi đặc quyền cho bạn")

            assertEquals(CaptureResult.Ignored("keyword:ưu đãi đặc quyền"), e.useCase(input))

            val log = e.repo.logs.single()
            assertEquals(NotificationStatus.IGNORED_SPAM, log.status)
            assertEquals("VCB", log.title)
            assertEquals("Ưu đãi đặc quyền cho bạn", log.content)
            assertEquals("keyword:ưu đãi đặc quyền", log.failureReason)
        }

    @Test
    fun `channel block stores the row and keeps title and content`() =
        runTest {
            val e = env(rule(allowlist = listOf("TXN")))

            assertEquals(CaptureResult.Ignored("channel:PROMOTION"), e.useCase(raw(channelId = "PROMOTION")))

            val log = e.repo.logs.single()
            assertEquals(NotificationStatus.IGNORED_SPAM, log.status)
            assertEquals("Biến động số dư", log.title)
            assertEquals("SD TK -50,000 tai quan", log.content)
        }

    @Test
    fun `keyword in title is caught`() =
        runTest {
            val e = env(rule(keywords = listOf("ưu đãi")))

            assertEquals(
                CaptureResult.Ignored("keyword:ưu đãi"),
                e.useCase(raw(title = "Ưu đãi", content = "Mua ngay")),
            )
        }

    @Test
    fun `keyword fragment inside a merchant name stays pending`() =
        runTest {
            val e = env(rule(keywords = listOf("ưu đãi")))

            assertEquals(CaptureResult.Stored, e.useCase(raw(content = "Thanh toan tai Kichi Hotpot -50,000")))
            assertEquals(
                CaptureResult.Stored,
                e.useCase(raw(content = "Chuyen tien cho Luu Dai Trang -50,000", postTime = 2L)),
            )
        }

    @Test
    fun `two rules merge keywords in priority order`() =
        runTest {
            val e =
                env(
                    rule(id = "b", priority = 2, keywords = listOf("ưu đãi")),
                    rule(id = "a", priority = 1, keywords = listOf("chúc mừng")),
                )

            assertEquals(
                CaptureResult.Ignored("keyword:chúc mừng"),
                e.useCase(raw(content = "Ưu đãi và chúc mừng")),
            )
        }

    @Test
    fun `null allowlists do not filter channels`() =
        runTest {
            val e = env(rule(allowlist = null))

            assertEquals(CaptureResult.Stored, e.useCase(raw(channelId = "PROMOTION")))
        }

    @Test
    fun `all allowlists set blocks other channel`() =
        runTest {
            val e = env(rule(id = "a", allowlist = listOf("TXN")), rule(id = "b", allowlist = listOf("ALERT")))

            assertEquals(CaptureResult.Ignored("channel:PROMOTION"), e.useCase(raw(channelId = "PROMOTION")))
            assertEquals(CaptureResult.Stored, e.useCase(raw(channelId = "ALERT", postTime = 2L)))
        }

    @Test
    fun `null channelId with filter on is ignored`() =
        runTest {
            val e = env(rule(allowlist = listOf("TXN")))

            assertEquals(CaptureResult.Ignored("channel:null"), e.useCase(raw(channelId = null)))
        }

    @Test
    fun `one null allowlist disables channel filter`() =
        runTest {
            val e = env(rule(id = "a", allowlist = listOf("TXN")), rule(id = "b", allowlist = null))

            assertEquals(CaptureResult.Stored, e.useCase(raw(channelId = "PROMOTION")))
        }

    @Test
    fun `clean notification is stored pending`() =
        runTest {
            val e = env(rule(keywords = listOf("ưu đãi")))

            assertEquals(CaptureResult.Stored, e.useCase(raw()))

            val log = e.repo.logs.single()
            assertEquals(NotificationStatus.PENDING, log.status)
            assertEquals("Biến động số dư", log.title)
            assertEquals("SD TK -50,000 tai quan", log.content)
            assertEquals(now.toEpochMilli(), log.receivedAt)
            assertNull(log.failureReason)
            UUID.fromString(log.id)
        }

    @Test
    fun `same notification twice is duplicate`() =
        runTest {
            val e = env(rule())

            assertEquals(CaptureResult.Stored, e.useCase(raw()))
            assertEquals(CaptureResult.Duplicate, e.useCase(raw()))
            assertEquals(1, e.repo.logs.size)
        }

    @Test
    fun `two spam notifications at same postTime differ`() =
        runTest {
            val e = env(rule())

            e.useCase(raw(content = "OTP 111111"))
            e.useCase(raw(content = "OTP 222222"))

            assertEquals(2, e.repo.logs.size)
        }

    @Test
    fun `passing notification that matches is pending with parsed fields`() =
        runTest {
            val e = env(rule())

            assertEquals(CaptureResult.Stored, e.useCase(raw(content = "SD TK -50,000 tai quan")))

            val log = e.repo.logs.single()
            assertEquals(NotificationStatus.PENDING, log.status)
            assertEquals("r1", log.matchedRuleId)
            assertEquals(50_000L, log.parsed?.amount)
            assertEquals(TransactionType.EXPENSE, log.parsed?.type)
            assertNull(log.failureReason)
        }

    @Test
    fun `passing notification without a match is failed`() =
        runTest {
            val e = env(rule(pattern = "nothing(?<amount>\\d+)"))

            assertEquals(CaptureResult.Failed, e.useCase(raw()))

            val log = e.repo.logs.single()
            assertEquals(NotificationStatus.FAILED, log.status)
            assertEquals("no_rule_matched", log.failureReason)
            assertNull(log.parsed)
            assertNull(log.matchedRuleId)
            assertEquals("Biến động số dư", log.title)
            assertEquals("SD TK -50,000 tai quan", log.content)
        }

    @Test
    fun `blocked notification is not parsed`() =
        runTest {
            val e = env(rule(keywords = listOf("ưu đãi")))

            assertEquals(CaptureResult.Ignored("keyword:ưu đãi"), e.useCase(raw(content = "Ưu đãi -50,000")))

            val log = e.repo.logs.single()
            assertNull(log.parsed)
            assertNull(log.matchedRuleId)
        }

    @Test
    fun `inactive rules still filter`() =
        runTest {
            val e = env(rule(keywords = listOf("ưu đãi đặc quyền"), isActive = false))

            assertEquals(
                CaptureResult.Ignored("keyword:ưu đãi đặc quyền"),
                e.useCase(raw(content = "Ưu đãi đặc quyền -50,000", postTime = 1L)),
            )
            assertEquals(CaptureResult.Failed, e.useCase(raw(postTime = 2L)))
        }

    private companion object {
        const val PKG = "com.test.bank"
    }
}
