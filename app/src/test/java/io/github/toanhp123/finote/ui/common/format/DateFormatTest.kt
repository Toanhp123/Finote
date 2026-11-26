package io.github.toanhp123.finote.ui.common.format

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.YearMonth
import java.util.Locale

@RunWith(AndroidJUnit4::class)
@Config(sdk = [37])
class DateFormatTest {
    private val november = YearMonth.of(2026, 11)

    @Test
    fun `formatMonthYear gives month and year`() {
        assertEquals("November 2026", formatMonthYear(november, Locale.forLanguageTag("en")))
        val vi = formatMonthYear(november, Locale.forLanguageTag("vi"))
        assertTrue(vi, vi.contains("2026") && vi.contains("11"))
    }
}
