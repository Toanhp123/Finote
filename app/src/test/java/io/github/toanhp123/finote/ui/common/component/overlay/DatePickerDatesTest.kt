package io.github.toanhp123.finote.ui.common.component.overlay

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class DatePickerDatesTest {
    @Test
    fun `epoch day two is one day of millis`() {
        assertEquals(86_400_000L, LocalDate.of(1970, 1, 2).toUtcEpochMillis())
    }

    @Test
    fun `round trip keeps the date in any time zone`() {
        val date = LocalDate.of(2026, 11, 3)
        assertEquals(date, date.toUtcEpochMillis().utcEpochMillisToLocalDate())
    }

    @Test
    fun `millis late in a utc day stay on that day`() {
        val lastMillis = LocalDate.of(2026, 11, 4).toUtcEpochMillis() - 1
        assertEquals(LocalDate.of(2026, 11, 3), lastMillis.utcEpochMillisToLocalDate())
    }
}
