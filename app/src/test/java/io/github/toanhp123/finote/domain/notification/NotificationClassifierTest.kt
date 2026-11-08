package io.github.toanhp123.finote.domain.notification

import io.github.toanhp123.finote.domain.notification.model.NotificationStatus
import io.github.toanhp123.finote.domain.notification.model.ParserRule
import io.github.toanhp123.finote.domain.notification.model.RawNotification
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationClassifierTest {
    private fun rule(
        keywords: List<String> = emptyList(),
        allowlist: List<String>? = null,
        pattern: String = "(?<sign>[+-])(?<amount>[\\d,]+)",
    ) = ParserRule(
        id = "r1",
        packageName = PKG,
        bankName = "Bank",
        channelIdAllowlist = allowlist,
        ignoreKeywords = keywords,
        priority = 0,
        isUserCustom = false,
        version = 1,
        regexPattern = pattern,
        fallbackTransactionType = null,
        dateFormatPattern = null,
        isActive = true,
        updatedAt = 0L,
    )

    private fun raw(
        content: String = "SD TK -50,000 tai quan",
        channelId: String? = "ch",
    ) = RawNotification(PKG, channelId, "Biến động số dư", content, 1_000L)

    @Test
    fun `secret text gives IGNORED_SPAM with isSecret`() {
        val result = classifyNotification(raw(content = "Mã OTP: 123456"), listOf(rule()))

        assertEquals(NotificationStatus.IGNORED_SPAM, result.status)
        assertEquals("secret:OTP", result.failureReason)
        assertTrue(result.isSecret)
        assertNull(result.parsed)
    }

    @Test
    fun `channel outside allowlist gives channel reason`() {
        val result = classifyNotification(raw(channelId = "PROMOTION"), listOf(rule(allowlist = listOf("TXN"))))

        assertEquals(NotificationStatus.IGNORED_SPAM, result.status)
        assertEquals("channel:PROMOTION", result.failureReason)
        assertFalse(result.isSecret)
    }

    @Test
    fun `ignore keyword gives keyword reason and keeps isSecret false`() {
        val result = classifyNotification(raw(content = "Ưu đãi -50,000"), listOf(rule(keywords = listOf("ưu đãi"))))

        assertEquals(NotificationStatus.IGNORED_SPAM, result.status)
        assertEquals("keyword:ưu đãi", result.failureReason)
        assertFalse(result.isSecret)
    }

    @Test
    fun `matching rule gives PENDING with parsed`() {
        val result = classifyNotification(raw(), listOf(rule()))

        assertEquals(NotificationStatus.PENDING, result.status)
        assertNull(result.failureReason)
        assertFalse(result.isSecret)
        assertEquals(50_000L, result.parsed?.fields?.amount)
    }

    @Test
    fun `no match gives FAILED no_rule_matched`() {
        val result = classifyNotification(raw(), listOf(rule(pattern = "nothing(?<amount>\\d+)")))

        assertEquals(NotificationStatus.FAILED, result.status)
        assertEquals("no_rule_matched", result.failureReason)
        assertFalse(result.isSecret)
        assertNull(result.parsed)
    }

    @Test
    fun `regexGroupNames lists named groups only`() {
        assertEquals(setOf("sign", "amount"), regexGroupNames("(?<sign>[+-])(?<amount>\\d+)(\\w)"))
        assertNotNull(regexGroupNames("abc"))
        assertTrue(regexGroupNames("abc").isEmpty())
    }

    private companion object {
        const val PKG = "com.test.bank"
    }
}
