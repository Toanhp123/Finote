package io.github.toanhp123.finote.domain.notification.service

import io.github.toanhp123.finote.domain.notification.testLog
import org.junit.Assert.assertEquals
import org.junit.Test

class SampleMaskingTest {
    @Test
    fun `only ASCII digits change`() =
        assertEquals("TK 1111 -111,111 VND 11m²", maskDigits("TK 0123 -450,000 VND 50m²"))

    @Test
    fun `spacing and line breaks survive`() = assertEquals("  A 11\n\tB  ", maskDigits("  A 09\n\tB  "))

    @Test
    fun `masked keeps package and every non-text field`() {
        val log = testLog(packageName = "com.VCB9", title = "SD 12", content = "x 34")

        assertEquals(log.copy(title = "SD 11", content = "x 11"), log.masked())
    }
}
