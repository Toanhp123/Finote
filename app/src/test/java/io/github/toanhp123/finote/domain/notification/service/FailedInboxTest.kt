package io.github.toanhp123.finote.domain.notification.service

import io.github.toanhp123.finote.domain.notification.model.NotificationLog
import io.github.toanhp123.finote.domain.notification.model.NotificationStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val BANK = "fake.bank"
private const val MONEY_TEXT = "Bạn vừa thanh toán 150,000đ tại Highlands Coffee"
private const val PROMO_TEXT = "Mời bạn ly cà phê đầu tuần, giảm 11K Circle K... dành riêng cho bạn"

@Suppress("LongParameterList") // why: one defaulted argument per log field keeps each test call to what it varies
private fun log(
    id: String,
    reason: String? = FAIL_NO_RULE_MATCHED,
    status: NotificationStatus = NotificationStatus.FAILED,
    packageName: String = BANK,
    title: String = "Thông báo",
    content: String = MONEY_TEXT,
    postTime: Long = 1_000L,
    key: String? = null,
) = NotificationLog(
    id = id,
    packageName = packageName,
    channelId = null,
    title = title,
    content = content,
    contentHash = "hash-$id",
    postTime = postTime,
    receivedAt = postTime + 5,
    status = status,
    failureReason = reason,
    notificationKey = key,
)

private fun inbox(
    vararg logs: NotificationLog,
    active: Set<String> = setOf(BANK),
) = buildFailedInbox(logs.toList(), active)

class FailedInboxTest {
    @Test
    fun `adjusted_after partner_failed user_dismissed stuck reasons are not items`() {
        val reasons =
            listOf(FAIL_ADJUSTED_AFTER, FAIL_PARTNER_FAILED, FAIL_USER_DISMISSED, FAIL_STUCK_REPOST, FAIL_STUCK_IGNORED)

        assertTrue(inbox(*reasons.mapIndexed { i, r -> log("l$i", reason = r) }.toTypedArray()).isEmpty())
    }

    @Test
    fun `settlement failures are always shown`() {
        val reasons =
            listOf(
                FAIL_AMBIGUOUS_ACCOUNT,
                FAIL_ACCOUNT_ARCHIVED,
                FAIL_INVALID_AMOUNT,
                FAIL_INVALID_TIME,
                FAIL_SETTLE_ERROR,
            )

        val items = inbox(*reasons.mapIndexed { i, r -> log("l$i", reason = r, content = PROMO_TEXT) }.toTypedArray())

        assertEquals(reasons.size, items.size)
        assertTrue(items.none { it.isHidden || it.isKeywordHidden })
        assertEquals(listOf("l0"), items.filter { it.reason == FAIL_AMBIGUOUS_ACCOUNT }.map { it.id })
    }

    @Test
    fun `promo no_rule_matched is hidden, abroad alert is shown`() {
        val abroad = log("abroad", title = "- KRW 11,111/VND 1,111,111", content = "Credit card: 1 .... .... 1111")
        val promo = log("promo", content = PROMO_TEXT, postTime = 2_000L)

        val items = inbox(abroad, promo).associateBy { it.id }

        assertFalse(items.getValue("abroad").isHidden)
        assertTrue(items.getValue("promo").isHidden)
        assertFalse(items.getValue("promo").isKeywordHidden)
    }

    @Test
    fun `package with no active rule gives no items`() {
        val logs =
            arrayOf(
                log("a"),
                log("b", reason = FAIL_SETTLE_ERROR),
                log("k", reason = "user_keyword:ad", status = NotificationStatus.IGNORED_SPAM),
            )

        assertTrue(inbox(*logs, active = setOf("other.bank")).isEmpty())
    }

    @Test
    fun `identical copies are one item with every log id`() {
        val older = log("a", postTime = 1_000L)
        val newer = log("b", postTime = 2_000L)
        val other = log("c", postTime = 3_000L, content = "$MONEY_TEXT khác")
        val otherPackage = log("d", postTime = 4_000L, packageName = "fake.wallet")

        val items = inbox(older, newer, other, otherPackage, active = setOf(BANK, "fake.wallet"))

        assertEquals(listOf("d", "c", "a"), items.map { it.id })
        assertEquals(listOf("a", "b"), items.single { it.id == "a" }.logIds)
        assertEquals(1_000L, items.single { it.id == "a" }.postTime)
    }

    @Test
    fun `an identical repost keeps the item id`() {
        val older = log("a", postTime = 1_000L)

        val before = inbox(older).single()
        val after = inbox(older, log("b", postTime = 2_000L)).single()

        assertEquals(before.id, after.id)
        assertEquals(listOf("a", "b"), after.logIds)
    }

    @Test
    fun `copies posted at the same time pick the lowest id`() {
        val item = inbox(log("b", postTime = 1_000L), log("a", postTime = 1_000L)).single()

        assertEquals("a", item.id)
        assertEquals(listOf("a", "b"), item.logIds)
    }

    @Test
    fun `keyword-hidden log is a hidden item flagged isKeywordHidden`() {
        val hidden = log("k", reason = "user_keyword:giam gia", status = NotificationStatus.IGNORED_SPAM)
        val ordinarySpam = log("s", reason = "keyword:otp", status = NotificationStatus.IGNORED_SPAM)

        val item = inbox(hidden, ordinarySpam).single()

        assertEquals("k", item.id)
        assertEquals(listOf("k"), item.logIds)
        assertTrue(item.isHidden)
        assertTrue(item.isKeywordHidden)
        assertEquals("user_keyword:giam gia", item.reason)
    }

    @Test
    fun `order is newest first`() {
        val items =
            inbox(
                log("a", postTime = 1_000L),
                log("c", postTime = 2_000L, reason = FAIL_SETTLE_ERROR),
                log("b", postTime = 2_000L, reason = FAIL_INVALID_TIME),
            )

        assertEquals(listOf("c", "b", "a"), items.map { it.id })
    }

    @Test
    fun `toString hides the text`() {
        assertEquals(
            "FailedInboxItem(id=a, reason=no_rule_matched, isHidden=false)",
            inbox(log("a")).single().toString(),
        )
    }
}
