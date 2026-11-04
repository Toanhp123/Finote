package io.github.toanhp123.finote.domain.transaction

import io.github.toanhp123.finote.domain.transaction.AmountResult.Empty
import io.github.toanhp123.finote.domain.transaction.AmountResult.Invalid
import io.github.toanhp123.finote.domain.transaction.AmountResult.NotPositive
import io.github.toanhp123.finote.domain.transaction.AmountResult.Ok
import io.github.toanhp123.finote.domain.transaction.CalcKey.BACKSPACE
import io.github.toanhp123.finote.domain.transaction.CalcKey.D0
import io.github.toanhp123.finote.domain.transaction.CalcKey.D1
import io.github.toanhp123.finote.domain.transaction.CalcKey.D2
import io.github.toanhp123.finote.domain.transaction.CalcKey.D3
import io.github.toanhp123.finote.domain.transaction.CalcKey.D4
import io.github.toanhp123.finote.domain.transaction.CalcKey.D5
import io.github.toanhp123.finote.domain.transaction.CalcKey.D7
import io.github.toanhp123.finote.domain.transaction.CalcKey.D9
import io.github.toanhp123.finote.domain.transaction.CalcKey.DIVIDE
import io.github.toanhp123.finote.domain.transaction.CalcKey.MINUS
import io.github.toanhp123.finote.domain.transaction.CalcKey.PLUS
import io.github.toanhp123.finote.domain.transaction.CalcKey.TIMES
import io.github.toanhp123.finote.domain.transaction.CalcKey.TRIPLE_ZERO
import org.junit.Assert.assertEquals
import org.junit.Test

class AmountExpressionTest {
    private fun type(vararg keys: CalcKey) = keys.fold("") { e, k -> AmountExpression.press(e, k) }

    private fun eval(vararg keys: CalcKey) = AmountExpression.evaluate(type(*keys))

    private fun nines(count: Int) = Array(count) { D9 }

    @Test
    fun `precedence`() {
        assertEquals(Ok(35_000), eval(D2, D0, TRIPLE_ZERO, PLUS, D5, TRIPLE_ZERO, TIMES, D3))
    }

    @Test
    fun `left to right same level`() {
        assertEquals(Ok(50), eval(D1, D0, D0, MINUS, D2, D0, MINUS, D3, D0))
        assertEquals(Ok(40), eval(D1, D0, D0, DIVIDE, D5, TIMES, D2))
    }

    @Test
    fun `division rounds half up`() {
        assertEquals(Ok(3), eval(D5, DIVIDE, D2))
        assertEquals(Ok(4), eval(D7, DIVIDE, D2))
        assertEquals(Ok(3), eval(D1, D0, DIVIDE, D4))
        assertEquals(Ok(3), eval(D1, D0, DIVIDE, D3))
    }

    @Test
    fun `division rounds at each step`() {
        assertEquals(Ok(99_999), eval(D1, D0, D0, TRIPLE_ZERO, DIVIDE, D3, TIMES, D3))
    }

    @Test
    fun `divide by zero`() {
        assertEquals(Invalid(InvalidReason.DIVIDE_BY_ZERO), eval(D5, DIVIDE, D0))
    }

    @Test
    fun `overflow`() {
        val keys = nines(13) + TIMES + nines(13)
        assertEquals(Invalid(InvalidReason.OVERFLOW), eval(*keys))
    }

    @Test
    fun `overflow through addition of two valid terms`() {
        val term = nines(13) + TIMES + D5 + D0 + D0 + D0 + D0 + D0 // about 5e18, below Long.MAX_VALUE
        assertEquals(Ok(4_999_999_999_999_500_000), eval(*term))
        assertEquals(Invalid(InvalidReason.OVERFLOW), eval(*(term + PLUS + term)))
    }

    @Test
    fun `division rounds without doubling a term above half of Long MAX_VALUE`() {
        // 2a would overflow a Long here; the quotient must not.
        val keys = nines(13) + TIMES + D5 + D0 + D0 + D0 + D0 + D0 + DIVIDE + D2
        assertEquals(Ok(2_499_999_999_999_750_000), eval(*keys))
    }

    @Test
    fun `trailing operator ignored`() {
        assertEquals(Ok(35_000), eval(D3, D5, TRIPLE_ZERO, PLUS))
    }

    @Test
    fun `operator replaces operator`() {
        assertEquals("5×3", type(D5, PLUS, TIMES, D3))
    }

    @Test
    fun `cannot start with operator`() {
        assertEquals("", type(PLUS))
    }

    @Test
    fun `max 13 digits`() {
        assertEquals(13, type(*nines(14)).length)
    }

    @Test
    fun `max 13 digits applies per number`() {
        assertEquals(13 + 1 + 2, type(*nines(13), PLUS, D1, D1).length)
    }

    @Test
    fun `triple zero fills to cap`() {
        assertEquals(13, type(*nines(11), TRIPLE_ZERO).length)
        assertEquals(13, type(*nines(12), TRIPLE_ZERO).length)
        assertEquals(13, type(*nines(13), TRIPLE_ZERO).length)
        assertEquals("5000", type(D5, TRIPLE_ZERO))
    }

    @Test
    fun `triple zero on empty gives zero`() {
        assertEquals("0", type(TRIPLE_ZERO))
        assertEquals("5+0", type(D5, PLUS, TRIPLE_ZERO))
    }

    @Test
    fun `leading zero replaced`() {
        assertEquals("5", type(D0, D5))
        assertEquals("0", type(D0, TRIPLE_ZERO))
    }

    @Test
    fun `backspace`() {
        assertEquals("12", type(D1, D2, PLUS, BACKSPACE))
        assertEquals("", type(BACKSPACE))
    }

    @Test
    fun `zero result not positive`() {
        assertEquals(NotPositive(0), eval(D1, DIVIDE, D3))
    }

    @Test
    fun `negative intermediate then positive is ok`() {
        assertEquals(NotPositive(-10), eval(D1, D0, MINUS, D2, D0))
        assertEquals(Ok(40), eval(D1, D0, MINUS, D2, D0, PLUS, D5, D0))
    }

    @Test
    fun `empty`() {
        assertEquals(Empty, AmountExpression.evaluate(""))
        assertEquals(Empty, eval(PLUS))
    }

    @Test
    fun `multiplication stays exact near the limit`() {
        assertEquals(
            Ok(4_000_000_000_000),
            eval(D4, TRIPLE_ZERO, TRIPLE_ZERO, TRIPLE_ZERO, TIMES, D1, TRIPLE_ZERO),
        )
    }
}
