package io.github.toanhp123.finote.ui.common.component.overlay

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

// The M3 DatePicker reads and returns UTC midnight millis, so the device time zone must not shift the day.
internal fun LocalDate.toUtcEpochMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

internal fun Long.utcEpochMillisToLocalDate(): LocalDate =
    Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()
