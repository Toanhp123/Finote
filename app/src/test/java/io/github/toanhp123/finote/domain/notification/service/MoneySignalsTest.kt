package io.github.toanhp123.finote.domain.notification.service

import io.github.toanhp123.finote.domain.notification.NotificationCorpusFixtures
import io.github.toanhp123.finote.domain.transaction.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val ABROAD_TITLE = "- KRW 11,111/VND 1,111,111"
private const val ABROAD_CONTENT =
    "1% Foreign Transaction Fee (Conditions Apply)\nCredit card: 1 .... .... 1111\n" +
        "Converted amount: VND 1,111,111\nAvailable limit: VND 111,111\n" +
        "Payment at (JOO)GIMCHISEUKOOL on 11/11/1111 at 11:11:11"
private const val PROMO_TITLE = "🥤 Mời bạn ly cà phê đầu tuần"
private const val PROMO_CONTENT =
    "Miễn phí loạt mã Highlands mua 1+1, Katinat mua 1+1, giảm 11K Circle K...  dành riêng cho bạn, " +
        "số lượng có hạn. Bấm vào nhận ngay!"

class MoneySignalsTest {
    @Test
    fun `abroad card alert is money`() = assertTrue(looksLikeMoney(ABROAD_TITLE, ABROAD_CONTENT))

    @Test
    fun `coffee promo is not money`() = assertFalse(looksLikeMoney(PROMO_TITLE, PROMO_CONTENT))

    @Test
    fun `payment without mask or sign is money`() =
        assertTrue(looksLikeMoney("", "Bạn vừa thanh toán 150,000đ tại Highlands Coffee"))

    @Test
    fun `balance change without sign is money`() =
        assertTrue(looksLikeMoney("", "Biến động số dư: 500,000 VND. Số dư hiện tại: 2,000,000 VND"))

    @Test
    fun `percent after dots is not a mask`() = assertFalse(looksLikeMoney("", "Giảm đến ...50% cho đơn từ 100K"))

    @Test
    fun `K after dots is not a mask`() = assertFalse(looksLikeMoney("", "Circle K... 50K"))

    @Test
    fun `every non-ignored corpus sample is money`() {
        val misses =
            NotificationCorpusFixtures.financialTransactions().filterNot { looksLikeMoney(it.title, it.text()) }
        assertTrue("not money: ${misses.map { it.id }}", misses.isEmpty())
    }

    @Test
    fun `report spam samples that read as money`() {
        val spam = NotificationCorpusFixtures.ignoredOrSpam()
        val reads = spam.filter { looksLikeMoney(it.title, it.text()) }
        println("SPAM_AS_MONEY: ${reads.size} of ${spam.size}: ${reads.map { it.id }}")
    }

    @Test
    fun `abroad amount is the VND figure`() = assertEquals(1_111_111L, extractVndAmount(ABROAD_TITLE, ABROAD_CONTENT))

    @Test
    fun `trailing dot zero zero is dropped`() =
        assertEquals(1_111_111L, extractVndAmount("", "Converted amount: VND 1,111,111.00"))

    @Test
    fun `trailing comma zero zero is dropped`() = assertEquals(1_111_111L, extractVndAmount("", "1.111.111,00 VND"))

    @Test
    fun `fee before amount is skipped`() =
        assertEquals(1_111_111L, extractVndAmount("", "Phí GD: VND 11,111\nConverted amount: VND 1,111,111"))

    @Test
    fun `balance only gives null`() = assertNull(extractVndAmount("", "Số dư: 2,000,000 VND"))

    @Test
    fun `signed amount wins over a title amount`() =
        assertEquals(50_000L, extractVndAmount("Tong 99,000 VND", "GD -50,000 VND"))

    @Test
    fun `unsigned change before balance is the amount`() =
        assertEquals(500_000L, extractVndAmount("", "Biến động số dư: 500,000 VND. Số dư hiện tại: 2,000,000 VND"))

    @Test
    fun `fee glued to its amount does not win over the real amount`() =
        assertEquals(1_111_111L, extractVndAmount("", "Phí 11,111 VND\nThanh toán VND 1,111,111"))

    @Test
    fun `fee plus balance gives null`() = assertNull(extractVndAmount("", "Phí 11,111 VND\nSố dư: 2,000,000 VND"))

    @Test
    fun `balance after a transaction label is not the amount`() =
        assertNull(extractVndAmount("", "Thanh toán tại X. Số dư 2,000,000 VND"))

    @Test
    fun `balance after a transaction sentence gives null`() =
        assertNull(extractVndAmount("", "Giao dịch thành công. Số dư: 2,000,000 VND"))

    @Test
    fun `amount before the balance sentence still wins`() =
        assertEquals(150_000L, extractVndAmount("", "Thanh toán tại X. 150,000 VND. Số dư 2,000,000 VND"))

    @Test
    fun `dong sign before the number is an amount`() {
        assertTrue(looksLikeMoney("", "Thanh toán ₫150,000"))
        assertEquals(150_000L, extractVndAmount("", "Thanh toán ₫150,000"))
    }

    @Test
    fun `dong letter after a space is an amount`() {
        assertTrue(looksLikeMoney("", "Thanh toán 150.000 đ"))
        assertEquals(150_000L, extractVndAmount("", "Thanh toán 150.000 đ"))
    }

    @Test
    fun `no break space before VND is an amount`() =
        assertEquals(150_000L, extractVndAmount("", "Thanh toán 150,000 VND"))

    @Test
    fun `sixteen digits overflow to null`() = assertNull(extractVndAmount("", "VND 1,111,111,111,111,111"))

    @Test
    fun `zero is null`() = assertNull(extractVndAmount("", "Thanh toan VND 0"))

    @Test
    fun `abroad mask is 1111`() = assertEquals("1111", extractMaskLast4(ABROAD_TITLE, ABROAD_CONTENT))

    @Test
    fun `abroad sign is expense`() =
        assertEquals(TransactionType.EXPENSE, extractSignedType(ABROAD_TITLE, ABROAD_CONTENT))

    @Test
    fun `plus sign is income`() = assertEquals(TransactionType.INCOME, extractSignedType("", "TK ...1234 +50,000 VND"))

    @Test
    fun `dash in a date is no sign`() = assertNull(extractSignedType("", "GD ngay 11-11-2026 so tien 50,000 VND"))

    @Test
    fun `abroad merchant`() = assertEquals("(JOO)GIMCHISEUKOOL", extractMerchant(ABROAD_TITLE, ABROAD_CONTENT))

    @Test
    fun `merchant after tai runs to end of line`() =
        assertEquals("Highlands Coffee", extractMerchant("", "Thanh toán 150,000đ tại Highlands Coffee"))

    @Test
    fun `no merchant marker gives null`() = assertNull(extractMerchant("", "Số dư: 2,000,000 VND"))
}

/** The text a failed log holds: expanded text when the sample has it. */
private fun io.github.toanhp123.finote.domain.notification.NotificationCorpusItem.text() = bigText ?: content
