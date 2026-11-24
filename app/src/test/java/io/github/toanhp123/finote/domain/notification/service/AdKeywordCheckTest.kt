package io.github.toanhp123.finote.domain.notification.service

import io.github.toanhp123.finote.domain.notification.model.AdKeywordRefusal
import io.github.toanhp123.finote.domain.notification.model.NotificationLog
import io.github.toanhp123.finote.domain.notification.model.NotificationStatus
import io.github.toanhp123.finote.domain.notification.testLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private const val KEYWORD = "ly cà phê đầu tuần"
private const val BANK = "Test bank"

class AdKeywordCheckTest {
    private fun unparsed(
        id: String,
        title: String,
        content: String,
    ) = testLog(
        id = id,
        title = title,
        content = content,
        status = NotificationStatus.FAILED,
        failureReason = FAIL_NO_RULE_MATCHED,
    )

    private val selected = unparsed("sel", "🥤 Mời bạn ly cà phê đầu tuần", "Nhận ưu đãi 20% cho ly thứ hai")

    private fun check(
        keyword: String,
        logs: List<NotificationLog> = listOf(selected),
        existing: List<String> = emptyList(),
    ) = checkAdKeyword(keyword, selected, logs, BANK, existing)

    private fun moneyLog(
        status: NotificationStatus,
        reason: String? = null,
    ) = testLog(
        id = "money-$status-$reason",
        title = "Thanh toan",
        content = "Thanh toan 150,000d tai quan, tang $KEYWORD",
        status = status,
        failureReason = reason,
    )

    @Test
    fun `two letters is TOO_SHORT`() {
        assertEquals(AdKeywordRefusal.TOO_SHORT, check("ly"))
        assertEquals(AdKeywordRefusal.TOO_SHORT, check("  ly  "))
    }

    @Test
    fun `keyword not in the selected log is NOT_IN_SELECTED`() {
        assertEquals(AdKeywordRefusal.NOT_IN_SELECTED, check("freeship"))
    }

    @Test
    fun `bank name is BANK_NAME`() {
        val bankAd = unparsed("sel", "Test bank gui ban uu dai", "Nhan ngay qua tang")
        assertEquals(AdKeywordRefusal.BANK_NAME, checkAdKeyword("TEST  BANK", bankAd, listOf(bankAd), BANK))
    }

    @Test
    fun `phrase from a confirmed log is IN_REAL_TRANSACTION`() {
        val confirmed = moneyLog(NotificationStatus.CONFIRMED)

        assertEquals(AdKeywordRefusal.IN_REAL_TRANSACTION, check(KEYWORD, listOf(selected, confirmed)))
    }

    @Test
    fun `every real money status refuses the phrase`() {
        val real =
            listOf(
                moneyLog(NotificationStatus.PENDING),
                moneyLog(NotificationStatus.CONFIRMED),
                moneyLog(NotificationStatus.DEDUPLICATED),
                moneyLog(NotificationStatus.PAIRED_TRANSFER),
                moneyLog(NotificationStatus.IGNORED_ACCOUNT),
                moneyLog(NotificationStatus.FAILED, FAIL_SETTLE_ERROR),
                moneyLog(NotificationStatus.FAILED, FAIL_USER_DISMISSED),
                moneyLog(NotificationStatus.FAILED, null),
            )

        real.forEach {
            assertEquals(
                "${it.status} ${it.failureReason}",
                AdKeywordRefusal.IN_REAL_TRANSACTION,
                check(KEYWORD, listOf(selected, it)),
            )
        }
    }

    @Test
    fun `an ignored log does not count as real money`() {
        val spam = moneyLog(NotificationStatus.IGNORED_SPAM, "keyword:otp")

        assertNull(check(KEYWORD, listOf(selected, spam)))
    }

    @Test
    fun `keyword matching another money-looking item is refused`() {
        val other = unparsed("other", "Highlands", "Thanh toan 150,000d tai Highlands, tang $KEYWORD")

        assertEquals(AdKeywordRefusal.IN_OTHER_MONEY_ITEM, check(KEYWORD, listOf(selected, other)))
    }

    @Test
    fun `identical copies of the selected text are not another money item`() {
        val moneyAd = unparsed("sel", "Hoan tien", "Hoan tien 50,000d khi thanh toan, tang $KEYWORD")
        val copy = moneyAd.copy(id = "copy")

        assertNull(checkAdKeyword(KEYWORD, moneyAd, listOf(moneyAd, copy), BANK))
    }

    @Test
    fun `real money wins over another money item`() {
        val other = unparsed("other", "Highlands", "Thanh toan 150,000d tai Highlands, tang $KEYWORD")
        val confirmed = moneyLog(NotificationStatus.CONFIRMED)

        assertEquals(
            AdKeywordRefusal.IN_REAL_TRANSACTION,
            check(KEYWORD, listOf(selected, other, confirmed)),
        )
    }

    @Test
    fun `a keyword the package already has is DUPLICATE`() {
        assertEquals(AdKeywordRefusal.DUPLICATE, check(KEYWORD, existing = listOf("ly ca phe dau tuan")))
    }

    @Test
    fun `promo phrase passes and counts other promos`() {
        val promoA = unparsed("a", "Cuoi tuan", "Mua 1 tang 1, $KEYWORD")
        val promoACopy = promoA.copy(id = "a2")
        val promoB = unparsed("b", "Hom nay", "Chi hom nay: $KEYWORD")
        val selectedCopy = selected.copy(id = "sel2")
        val unrelated = unparsed("c", "Tin khac", "Cap nhat ung dung")
        val logs = listOf(selected, selectedCopy, promoA, promoACopy, promoB, unrelated)

        assertNull(check(KEYWORD, logs))
        assertEquals(2, countOtherHiddenItems(KEYWORD, selected, logs))
        assertEquals(
            listOf("sel", "sel2", "a", "a2", "b"),
            adKeywordMatches(KEYWORD, logs).map { it.id },
        )
    }

    @Test
    fun `only unparsed logs are hidden`() {
        val confirmed = moneyLog(NotificationStatus.CONFIRMED)

        assertEquals(listOf("sel"), adKeywordMatches(KEYWORD, listOf(selected, confirmed)).map { it.id })
    }

    @Test
    fun `suggestion strips emoji`() {
        assertEquals("Mời bạn ly cà phê đầu tuần", suggestAdKeyword("🥤 Mời bạn ly cà phê đầu tuần"))
    }

    @Test
    fun `suggestion strips edge punctuation and inner emoji`() {
        assertEquals("Ưu đãi hôm nay", suggestAdKeyword("“Ưu đãi hôm nay!” 🎉"))
        assertEquals("Mời bạn ly", suggestAdKeyword("Mời 🥤  bạn ly ..."))
        assertEquals("", suggestAdKeyword("🎉!!"))
    }
}
