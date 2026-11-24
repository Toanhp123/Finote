package io.github.toanhp123.finote.ui.settings.accountedit.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StartBalanceInputTest {
    @Test
    fun `empty or non-numeric text is no value`() {
        assertNull(parseStartBalance(""))
        assertNull(parseStartBalance("-"))
        assertNull(parseStartBalance("abc"))
    }

    @Test
    fun `digits are kept and other characters dropped`() {
        assertEquals(1_250_000L, parseStartBalance("1250000"))
        assertEquals(123L, parseStartBalance("1a2 3"))
        assertEquals(0L, parseStartBalance("0"))
    }

    @Test
    fun `a leading minus is kept, a minus later is dropped`() {
        assertEquals(-5_000L, parseStartBalance("-5000"))
        assertEquals(50L, parseStartBalance("5-0"))
    }

    @Test
    fun `a long text is cut so it cannot overflow`() {
        assertEquals(999_999_999_999_999L, parseStartBalance("9999999999999999999999"))
    }
}
