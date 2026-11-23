package io.github.toanhp123.finote.ui.common.format

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class MoneyFormatTest {
    private val vi = Locale.forLanguageTag("vi")
    private val en = Locale.forLanguageTag("en")

    @Test
    fun `vietnamese puts the symbol after a no-break space`() {
        assertEquals("1.234.567\u00A0₫", formatMoney(1_234_567, MoneyKind.Neutral, vi))
    }

    @Test
    fun `english chinese japanese put the symbol first`() {
        listOf("en", "zh-CN", "ja").forEach {
            assertEquals(it, "₫1,234,567", formatMoney(1_234_567, MoneyKind.Neutral, Locale.forLanguageTag(it)))
        }
    }

    @Test
    fun `income gets a plus and expense a minus sign`() {
        assertEquals("+45.000\u00A0₫", formatMoney(45_000, MoneyKind.Income, vi))
        assertEquals("\u221245.000\u00A0₫", formatMoney(45_000, MoneyKind.Expense, vi))
        assertEquals("\u2212₫45,000", formatMoney(45_000, MoneyKind.Expense, en))
    }

    @Test
    fun `transfer and zero carry no sign`() {
        assertEquals("45.000\u00A0₫", formatMoney(45_000, MoneyKind.Transfer, vi))
        assertEquals("0\u00A0₫", formatMoney(0, MoneyKind.Income, vi))
        assertEquals("0\u00A0₫", formatMoney(0, MoneyKind.Expense, vi))
    }

    @Test
    fun `negative neutral shows minus and absolute value`() {
        assertEquals("\u22122.500.000\u00A0₫", formatMoney(-2_500_000, MoneyKind.Neutral, vi))
    }

    @Test
    fun `largest long formats in full`() {
        assertEquals("₫9,223,372,036,854,775,807", formatMoney(Long.MAX_VALUE, MoneyKind.Neutral, en))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `smallest long is rejected`() {
        formatMoney(Long.MIN_VALUE, MoneyKind.Neutral, vi)
    }
}
