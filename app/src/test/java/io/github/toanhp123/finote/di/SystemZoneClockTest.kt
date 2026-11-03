package io.github.toanhp123.finote.di

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.TimeZone

class SystemZoneClockTest {
    @Test
    fun `zone follows the current system default`() {
        val saved = TimeZone.getDefault()
        try {
            val clock = SystemZoneClock()
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
            assertEquals(ZoneId.of("UTC"), clock.zone)
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"))
            assertEquals(ZoneId.of("Asia/Ho_Chi_Minh"), clock.zone)
            assertEquals(YearMonth.now(ZoneId.of("Asia/Ho_Chi_Minh")), YearMonth.now(clock))
        } finally {
            TimeZone.setDefault(saved)
        }
    }

    @Test
    fun `withZone pins the given zone`() {
        assertEquals(ZoneOffset.UTC, SystemZoneClock().withZone(ZoneOffset.UTC).zone)
    }
}
